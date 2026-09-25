package com.example.uselocalrecipes.recipe;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * Reads the recipe files that ship with the game, the mods and the local data packs.
 *
 * <p>It builds a real data pack stack and runs the vanilla loading path instead of parsing json by hand,
 * so ingredients referring to item tags resolve against the tags of those packs.
 */
public final class LocalRecipeLoader {

    private static final int MAX_REPORTED_FAILURES = 20;

    private LocalRecipeLoader() {
    }

    public static LocalRecipeData load(RegistryAccess registries, List<PackResources> modPacks) {
        // Later packs override earlier ones, so vanilla goes first.
        List<PackResources> packs = new ArrayList<>(modPacks.size() + 1);
        packs.add(ServerPacksSource.createVanillaPackSource().fullResources());
        packs.addAll(modPacks);

        FileToIdConverter lister = FileToIdConverter.registry(Registries.RECIPE);
        SortedMap<Identifier, Recipe<?>> parsed = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        List<Registry.PendingTags<?>> pendingTags = List.of();
        int fileCount = 0;
        int failedCount = 0;

        try (CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
            // Load their tags first so ingredients referring to item tags resolve.
            RegistryAccess.Frozen frozen = registries.freeze();
            pendingTags = TagLoader.loadTagsForExistingRegistries(resources, frozen);
            HolderLookup.Provider lookup = HolderLookup.Provider.create(
                    TagLoader.buildUpdatedLookups(frozen, pendingTags).stream());
            RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);

            for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(resources).entrySet()) {
                fileCount++;
                Identifier id = lister.fileToId(entry.getKey());

                try (Reader reader = entry.getValue().openAsReader()) {
                    Recipe<?> recipe = Recipe.DIRECT_CODEC.parse(ops, StrictJsonParser.parse(reader))
                            .getOrThrow(message -> new JsonParseException(message));
                    parsed.put(id, recipe);
                } catch (Exception e) {
                    failedCount++;
                    if (failures.size() < MAX_REPORTED_FAILURES) {
                        failures.add(id + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to read local recipes", e);
            return LocalRecipeData.EMPTY;
        }

        List<RecipeHolder<?>> holders = new ArrayList<>(parsed.size());
        parsed.forEach((id, recipe) -> holders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe)));

        if (failedCount > 0) {
            Constants.LOG.info("Skipped {} of {} local recipe files that could not be parsed, usually recipes of mods the server does not have",
                    failedCount, fileCount);
            if (UseLocalRecipesConfig.get().logFailedRecipes) {
                failures.forEach(failure -> Constants.LOG.info("  {}", failure));
            }
        }

        return new LocalRecipeData(RecipeMerger.toRecipeMap(holders), fileCount, failedCount, pendingTags);
    }
}
