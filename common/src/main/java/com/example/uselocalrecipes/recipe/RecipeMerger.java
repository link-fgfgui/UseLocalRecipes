package com.example.uselocalrecipes.recipe;

import com.mojang.serialization.Lifecycle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Combines the recipes the server sent with the recipes that were read from local files.
 */
public final class RecipeMerger {

    private RecipeMerger() {
    }

    /**
     * Merges the two recipe sets. Recipes are deduplicated by id, with the server always winning.
     * When {@code preferServerTypes} is set, locally read recipes of a recipe type the server sent are
     * dropped entirely, because the server clearly knows that type better than the local files do.
     */
    public static RecipeMap merge(RecipeMap serverRecipes, Set<RecipeType<?>> serverTypes, RecipeMap localRecipes, boolean preferServerTypes) {
        if (localRecipes.values().isEmpty()) {
            return serverRecipes;
        }

        Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> byId = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : serverRecipes.values()) {
            byId.put(holder.id(), holder);
        }
        for (RecipeHolder<?> holder : localRecipes.values()) {
            if (preferServerTypes && serverTypes.contains(holder.value().getType())) {
                continue;
            }
            byId.putIfAbsent(holder.id(), holder);
        }

        return toRecipeMap(byId.values());
    }

    /**
     * Turns a bunch of recipes into a {@link RecipeMap}.
     *
     * <p>Since 26.3 recipes are registry entries, so the map has to be built through a lookup. The holder
     * keys are the recipe ids, which is what {@link RecipeMap#byKey} and the viewers use.
     */
    public static RecipeMap toRecipeMap(Collection<RecipeHolder<?>> holders) {
        MappedRegistry<Recipe<?>> registry = new MappedRegistry<>(Registries.RECIPE, Lifecycle.stable());
        for (RecipeHolder<?> holder : holders) {
            registry.register(holder.id(), holder.value(), RegistrationInfo.BUILT_IN);
        }

        return RecipeMap.create(registry.freeze());
    }

    public static Set<RecipeType<?>> typesOf(RecipeMap recipes) {
        return recipes.values().stream()
                .map(holder -> holder.value().getType())
                .collect(Collectors.toUnmodifiableSet());
    }
}
