package com.example.uselocalrecipes.runtime;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.Services;
import com.example.uselocalrecipes.recipe.LocalRecipeData;
import com.example.uselocalrecipes.recipe.LocalRecipeLoader;
import com.example.uselocalrecipes.recipe.RecipeMerger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Drives the whole flow: wait for the recipes the server sends, read recipes from local files in the
 * meantime, then hand the merged result to the loader's client recipe synchronization.
 *
 * <p>Everything in here happens on the client thread, except for the file reading which runs on a
 * separate thread so that joining a world does not stall.
 */
public final class RecipeSyncController {

    private static final ExecutorService FILE_READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "uselocalrecipes-reader");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicReference<CompletableFuture<LocalRecipeData>> localLoad = new AtomicReference<>();
    private static final AtomicReference<LocalRecipeData> localData = new AtomicReference<>();

    private static Set<RecipeType<?>> serverTypes = Set.of();
    private static RecipeMap serverRecipes = RecipeMap.EMPTY;
    private static boolean injected = false;
    private static boolean injecting = false;
    private static boolean tagsApplied = false;
    private static int ticksUntilInject = -1;

    private RecipeSyncController() {
    }

    /** Called when the client joins a world. */
    public static void onClientJoin() {
        reset();
        if (!UseLocalRecipesConfig.get().enabled) {
            Constants.LOG.info("Use Local Recipes is disabled in the config");
            return;
        }

        ticksUntilInject = UseLocalRecipesConfig.get().syncDelayTicks;
        startLocalRead();
    }

    /** Called when the client leaves a world. */
    public static void onClientDisconnect() {
        reset();
    }

    /** Called every client tick. */
    public static void onClientTick() {
        if (ticksUntilInject > 0) {
            ticksUntilInject--;
        }
        if (ticksUntilInject == 0 && !injected && localData.get() != null) {
            inject();
        }
    }

    /**
     * Called when the server sent recipes. While this is running the recipes are applied by the loader,
     * see {@link #injecting}.
     */
    public static void onServerRecipesReceived(Set<RecipeType<?>> types, RecipeMap recipes) {
        if (injecting) {
            // Our own injected recipes coming back to us.
            return;
        }

        serverTypes = Set.copyOf(types);
        serverRecipes = recipes;
        Constants.LOG.info("Server sent {} recipes of {} type(s)", recipes.values().size(), serverTypes.size());

        if (injected) {
            // Data arrived after we already fell back to local files, merge again so the server wins.
            inject();
        } else if (ticksUntilInject < 0) {
            ticksUntilInject = 0;
        } else {
            ticksUntilInject = Math.min(ticksUntilInject, 1);
        }
    }

    /** Re-reads the local files, used by the /ulr reload command. */
    public static void reloadLocalRecipes() {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        localData.set(null);
        localLoad.set(null);
        injected = false;
        ticksUntilInject = 1;
        startLocalRead();
    }

    public static RecipeMap localRecipes() {
        LocalRecipeData data = localData.get();
        return data != null ? data.recipes() : RecipeMap.EMPTY;
    }

    public static Status status() {
        LocalRecipeData data = localData.get();
        int localCount = data != null ? data.recipes().values().size() : 0;
        return new Status(
                UseLocalRecipesConfig.get().enabled,
                !serverTypes.isEmpty() || !serverRecipes.values().isEmpty(),
                serverTypes,
                serverRecipes.values().size(),
                localCount,
                data != null ? data.fileCount() : 0,
                data != null ? data.failedCount() : 0,
                data != null ? RecipeMerger.merge(serverRecipes, serverTypes, data.recipes(), UseLocalRecipesConfig.get().preferServerTypes).values().size() : 0,
                injected,
                data != null ? data.failures() : List.of()
        );
    }

    private static void reset() {
        localLoad.set(null);
        localData.set(null);
        serverTypes = Set.of();
        serverRecipes = RecipeMap.EMPTY;
        injected = false;
        tagsApplied = false;
        ticksUntilInject = -1;
    }

    /**
     * Reads the recipes that are available on this machine. In singleplayer the integrated server is the
     * better source, everywhere else the game and mod files are read.
     */
    private static void startLocalRead() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        MinecraftServer server = client.getSingleplayerServer();
        if (server != null && !server.getRecipeManager().getRecipes().isEmpty()) {
            LocalRecipeData data = new LocalRecipeData(
                    RecipeMerger.toRecipeMap(server.getRecipeManager().getRecipes()),
                    server.getRecipeManager().getRecipes().size(), 0, List.of());
            Constants.LOG.info("Using {} recipes of the integrated server", server.getRecipeManager().getRecipes().size());
            localData.set(data);
            localLoad.set(CompletableFuture.completedFuture(data));
            return;
        }

        RegistryAccess registries = level.registryAccess();
        List<PackResources> modPacks = Services.RECIPES.createModDataPacks();
        CompletableFuture<LocalRecipeData> future = CompletableFuture.supplyAsync(
                () -> LocalRecipeLoader.load(registries, modPacks), FILE_READER);
        future.whenComplete((data, error) -> {
            if (data != null) {
                localData.set(data);
                Constants.LOG.info("Read {} local recipes from {} files", data.recipes().values().size(), data.fileCount());
            } else if (error != null) {
                Constants.LOG.error("Failed to read local recipes", error);
            }
        });
        localLoad.set(future);
    }

    private static void inject() {
        LocalRecipeData data = localData.get();
        if (data != null && !tagsApplied) {
            data.applyPendingTags();
            tagsApplied = true;
        }
        RecipeMap localRecipes = data != null ? data.recipes() : RecipeMap.EMPTY;
        UseLocalRecipesConfig config = UseLocalRecipesConfig.get();
        RecipeMap merged = RecipeMerger.merge(serverRecipes, serverTypes, localRecipes, config.preferServerTypes);
        Set<RecipeType<?>> mergedTypes = RecipeMerger.typesOf(merged);

        int localUsed = merged.values().size() - serverRecipes.values().size();
        Constants.LOG.info("Injecting {} recipes ({} from the server, {} read locally, {} type(s) owned by the server)",
                merged.values().size(), serverRecipes.values().size(), localUsed, serverTypes.size());

        injecting = true;
        try {
            Services.RECIPES.injectRecipes(mergedTypes, merged);
            injected = true;
            // Viewers that already built their recipe list from the data of the server need to rebuild it.
            JeiReloadHook.requestReload();
        } catch (Throwable t) {
            Constants.LOG.error("Failed to inject recipes", t);
        } finally {
            injecting = false;
        }
    }

    /**
     * @param enabled            whether the mod is enabled in the config
     * @param serverDataReceived whether the server sent any recipes
     * @param serverTypes        the recipe types the server sent
     * @param serverRecipeCount  number of recipes the server sent
     * @param localRecipeCount   number of recipes read from local files
     * @param localFileCount     number of local recipe files that were found
     * @param localFailedCount   number of local recipe files that could not be parsed
     * @param mergedRecipeCount  number of recipes that would currently be handed to the viewers
     * @param injected           whether recipes were handed to the viewers already
     * @param failures           descriptions of local recipe files that could not be parsed
     */
    public record Status(boolean enabled, boolean serverDataReceived, Set<RecipeType<?>> serverTypes, int serverRecipeCount,
                         int localRecipeCount, int localFileCount, int localFailedCount, int mergedRecipeCount,
                         boolean injected, List<String> failures) {

        public int localUsedCount() {
            return mergedRecipeCount - serverRecipeCount;
        }
    }
}
