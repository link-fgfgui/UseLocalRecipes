package com.example.uselocalrecipes.recipe;

import com.example.uselocalrecipes.Constants;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * Result of reading the recipes that are available on the client machine.
 *
 * @param recipes     every recipe that could be parsed
 * @param fileCount   number of recipe files that were found
 * @param failedCount number of recipe files that could not be parsed
 * @param failures    descriptions of the first few failures, for diagnostics
 * @param pendingTags pending tags loaded from the mod and game data packs
 */
public record LocalRecipeData(RecipeMap recipes, int fileCount, int failedCount, List<String> failures,
                              List<Registry.PendingTags<?>> pendingTags) {

    public static final LocalRecipeData EMPTY = new LocalRecipeData(RecipeMap.EMPTY, 0, 0, List.of(), List.of());

    public LocalRecipeData(RecipeMap recipes, int fileCount, int failedCount, List<String> failures) {
        this(recipes, fileCount, failedCount, failures, List.of());
    }

    /**
     * Applies the tags that were loaded alongside the local recipes. Must be called on the client thread
     * before the recipes are handed to recipe viewers, otherwise custom ingredients that evaluate tags
     * fail because the tag is unbound.
     */
    public void applyPendingTags() {
        for (Registry.PendingTags<?> pendingTag : pendingTags) {
            try {
                pendingTag.apply();
            } catch (Exception e) {
                Constants.LOG.error("Failed to apply pending tags for registry {}", pendingTag.key(), e);
            }
        }
    }
}
