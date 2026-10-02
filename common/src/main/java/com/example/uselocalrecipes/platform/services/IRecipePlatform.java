package com.example.uselocalrecipes.platform.services;

import java.util.List;
import java.util.Set;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/** Loader specific parts of the recipe flow, implemented by the fabric and neoforge modules. */
public interface IRecipePlatform {

    /** A data pack for every loaded mod, lowest priority first. The vanilla pack is added by LocalRecipeLoader. */
    List<PackResources> createModDataPacks();

    /** Hands the merged recipes to the loader's client recipe synchronization, so viewers see them as server data. */
    void injectRecipes(Set<RecipeType<?>> recipeTypes, RecipeMap recipes);

    /** Registers the loader specific client hooks, called once from the client entry point. */
    void registerClientLifecycle();
}
