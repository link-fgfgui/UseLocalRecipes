package com.example.uselocalrecipes.recipe;

import com.example.uselocalrecipes.Constants;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.world.item.crafting.RecipeMap;

/** Result of reading the recipes that are available on the client machine. */
public record LocalRecipeData(RecipeMap recipes, int fileCount, int failedCount,
                              List<Registry.PendingTags<?>> pendingTags) {

    public static final LocalRecipeData EMPTY = new LocalRecipeData(RecipeMap.EMPTY, 0, 0, List.of());

    public LocalRecipeData(RecipeMap recipes, int fileCount, int failedCount) {
        this(recipes, fileCount, failedCount, List.of());
    }

    /** Must run on the client thread before the recipes reach the viewers, otherwise tag based ingredients fail. */
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
