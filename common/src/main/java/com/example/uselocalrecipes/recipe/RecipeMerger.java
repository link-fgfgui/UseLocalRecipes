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

/** Combines the recipes the server sent with the recipes that were read from local files. */
public final class RecipeMerger {

    private RecipeMerger() {
    }

    /**
     * Deduplicates by id with the server always winning. With {@code preferServerTypes}, locally read
     * recipes of a type the server sent are dropped, the server knows that type better.
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

    /** Since 26.3 recipes are registry entries, the map has to be built through a lookup keyed by recipe id. */
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
