package com.example.uselocalrecipes.platform.services;

import java.util.List;
import java.util.Set;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Loader specific parts of the recipe flow. Implemented by the fabric and neoforge modules.
 */
public interface IRecipePlatform {

    /**
     * Returns a data pack for every loaded mod, ordered from lowest to highest priority.
     * The vanilla data pack is appended by the common code, so mods always override vanilla.
     */
    List<PackResources> createModDataPacks();

    /**
     * Hands recipes to the loader's client recipe synchronization, which makes every client side recipe
     * viewer (JEI, EMI, ...) see them exactly as if the server itself had sent them.
     *
     * @param recipeTypes the recipe types the given recipes belong to
     * @param recipes     the merged recipe set
     */
    void injectRecipes(Set<RecipeType<?>> recipeTypes, RecipeMap recipes);

    /**
     * Registers the loader specific client hooks that drive the recipe synchronization.
     * Called once from the client entry point.
     */
    void registerClientLifecycle();
}
