package com.example.uselocalrecipes.runtime;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.Services;
import com.example.uselocalrecipes.recipe.LocalRecipeData;
import com.example.uselocalrecipes.recipe.LocalRecipeLoader;
import com.example.uselocalrecipes.recipe.RecipeMerger;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Waits for the recipes the server sends, reads recipes from local files in the meantime, then hands the
 * merged result to the loader's client recipe synchronization.
 *
 * <p>Only remote servers are handled, the integrated server of a singleplayer world already owns the
 * recipes. Everything runs on the client thread except the file reading, so joining a world does not stall.
 */
public final class RecipeSyncController {

    /** Upper bound for how much a single status log lists. */
    private static final int MAX_LOGGED_TYPES = 20;
    private static final int MAX_LOGGED_RECIPES = 40;

    private static final ExecutorService FILE_READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "uselocalrecipes-reader");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicReference<LocalRecipeData> localData = new AtomicReference<>();

    /** Bumped on every reset, so a read that finishes after a world change cannot write stale data. */
    private static volatile int readGeneration;

    private static Set<RecipeType<?>> serverTypes = Set.of();
    private static RecipeMap serverRecipes = RecipeMap.EMPTY;
    /** Whether this session reads and injects recipes at all, false in singleplayer and when disabled. */
    private static boolean active = false;
    private static boolean injected = false;
    private static boolean injecting = false;
    private static boolean injectionFailed = false;
    private static boolean tagsApplied = false;
    private static boolean localRecipeWarningSent = false;
    private static int ticksUntilInject = -1;

    private RecipeSyncController() {
    }

    public static void onClientJoin() {
        reset();
        if (!UseLocalRecipesConfig.get().enabled) {
            Constants.LOG.info("Use Local Recipes is disabled in the config");
            return;
        }

        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            // The integrated server sent everything already, doing nothing here cannot break anything.
            Constants.LOG.info("Singleplayer, the integrated server owns the recipes, nothing to do");
            return;
        }

        active = true;
        ticksUntilInject = UseLocalRecipesConfig.get().syncDelayTicks;
        startLocalRead();
    }

    public static void onClientDisconnect() {
        reset();
    }

    public static void onClientTick() {
        if (!active) {
            return;
        }
        if (ticksUntilInject > 0) {
            ticksUntilInject--;
        }
        if (!injectionFailed && ticksUntilInject == 0 && !injected && localData.get() != null) {
            inject();
        }
    }

    /** Used by the platforms to ignore the sync events their own injection causes. */
    public static boolean isInjecting() {
        return injecting;
    }

    public static void onServerRecipesReceived(Set<RecipeType<?>> types, RecipeMap recipes) {
        if (!active || injecting) {
            // Not our session, or our own injected recipes coming back to us.
            return;
        }

        serverTypes = Set.copyOf(types);
        serverRecipes = recipes;
        injectionFailed = false;
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

    /** Logs the state of the sync, this is the only place it is reported. */
    private static void logStatus(RecipeMap merged) {
        LocalRecipeData data = localData.get();
        boolean serverDataReceived = !serverTypes.isEmpty() || !serverRecipes.values().isEmpty();

        Constants.LOG.info("Use Local Recipes: enabled={}, injected={}", UseLocalRecipesConfig.get().enabled, injected);
        Constants.LOG.info("  server: {} recipes, {} types{}", serverRecipes.values().size(), serverTypes.size(),
                serverDataReceived ? "" : " (nothing received)");
        Constants.LOG.info("  local: {} recipes from {} files, {} unreadable",
                data != null ? data.recipes().values().size() : 0,
                data != null ? data.fileCount() : 0,
                data != null ? data.failedCount() : 0);
        Constants.LOG.info("  viewer sees: {} recipes, {} of them from local files",
                merged.values().size(), merged.values().size() - serverRecipes.values().size());
        if (!serverTypes.isEmpty()) {
            Constants.LOG.info("  types owned by the server: {}", typeNames(serverTypes));
        }

        logLocalRecipes(data != null ? data.recipes() : RecipeMap.EMPTY);
    }

    /** Logs how many local recipes were read per type, the individual ids only at debug level. */
    private static void logLocalRecipes(RecipeMap localRecipes) {
        Map<RecipeType<?>, List<RecipeHolder<?>>> byType = localRecipes.values().stream()
                .collect(Collectors.groupingBy(holder -> holder.value().getType()));
        byType.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<RecipeType<?>, List<RecipeHolder<?>>> entry) -> entry.getValue().size()).reversed())
                .limit(MAX_LOGGED_TYPES)
                .forEach(entry -> Constants.LOG.info("  local {}: {}", typeName(entry.getKey()), entry.getValue().size()));

        if (Constants.LOG.isDebugEnabled()) {
            byType.forEach((type, holders) -> holders.stream()
                    .limit(MAX_LOGGED_RECIPES)
                    .forEach(holder -> Constants.LOG.debug("  local {}: {}", typeName(type), holder.id().identifier())));
        }
    }

    private static String typeNames(Collection<RecipeType<?>> types) {
        return types.stream().map(RecipeSyncController::typeName).sorted().collect(Collectors.joining(", "));
    }

    private static String typeName(RecipeType<?> type) {
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id != null ? id.toString() : type.toString();
    }

    private static void reset() {
        readGeneration++;
        localData.set(null);
        serverTypes = Set.of();
        serverRecipes = RecipeMap.EMPTY;
        active = false;
        injected = false;
        injectionFailed = false;
        tagsApplied = false;
        localRecipeWarningSent = false;
        ticksUntilInject = -1;
    }

    /** Reads the game and mod files, only called for remote servers. */
    private static void startLocalRead() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        RegistryAccess registries = level.registryAccess();
        List<PackResources> modPacks = Services.RECIPES.createModDataPacks();
        int generation = readGeneration;
        CompletableFuture<LocalRecipeData> future = CompletableFuture.supplyAsync(
                () -> LocalRecipeLoader.load(registries, modPacks), FILE_READER);
        future.whenComplete((data, error) -> {
            if (generation != readGeneration) {
                // The world changed while reading, this data belongs to a session that is gone.
                return;
            }
            if (data != null) {
                localData.set(data);
                Constants.LOG.info("Read {} local recipes from {} files", data.recipes().values().size(), data.fileCount());
            } else if (error != null) {
                Constants.LOG.error("Failed to read local recipes", error);
            }
        });
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
        } catch (Throwable t) {
            // Retrying every tick would only spam the log, a new world or new server data clears this again.
            injectionFailed = true;
            Constants.LOG.error("Failed to inject recipes", t);
        } finally {
            injecting = false;
        }

        if (injected) {
            notifyLocalRecipes(localUsed);
            logStatus(merged);
        }
    }

    /** Warns the player once per world that part of the recipe list is local and may not match the server. */
    private static void notifyLocalRecipes(int localUsed) {
        if (localUsed <= 0 || localRecipeWarningSent || !UseLocalRecipesConfig.get().warnAboutLocalRecipes) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            localRecipeWarningSent = true;
            player.sendSystemMessage(Component.translatable("uselocalrecipes.message.local_recipes", localUsed));
        }
    }
}
