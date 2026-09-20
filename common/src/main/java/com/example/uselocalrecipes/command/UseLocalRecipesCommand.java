package com.example.uselocalrecipes.command;

import com.example.uselocalrecipes.runtime.RecipeSyncController;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The logic behind the client side {@code /ulr} command. Both loaders build their own command tree around
 * these methods, because fabric's client commands use a different command source type.
 */
public final class UseLocalRecipesCommand {

    private static final int MAX_LISTED = 40;

    private UseLocalRecipesCommand() {
    }

    /** Ids of every registered recipe type, used for command suggestions. */
    public static Collection<String> recipeTypeIds() {
        return BuiltInRegistries.RECIPE_TYPE.keySet().stream().map(ResourceLocation::toString).toList();
    }

    public static void status(Consumer<Component> reply) {
        RecipeSyncController.Status status = RecipeSyncController.status();

        reply.accept(Component.literal("Use Local Recipes").withStyle(ChatFormatting.GOLD));
        reply.accept(Component.literal("  enabled: " + status.enabled() + ", injected: " + status.injected()));
        reply.accept(Component.literal("  server: " + status.serverRecipeCount() + " recipes, "
                + status.serverTypes().size() + " types" + (status.serverDataReceived() ? "" : " (nothing received)")));
        reply.accept(Component.literal("  local: " + status.localRecipeCount() + " recipes from "
                + status.localFileCount() + " files, " + status.localFailedCount() + " unreadable"));
        reply.accept(Component.literal("  viewer sees: " + status.mergedRecipeCount() + " recipes, "
                + status.localUsedCount() + " of them from local files"));

        if (!status.serverTypes().isEmpty()) {
            reply.accept(Component.literal("  types owned by the server: " + typeNames(status.serverTypes())));
        }
        if (status.localFailedCount() > 0) {
            reply.accept(Component.literal("  enable logFailedRecipes in the config to see why files were skipped"));
        }
    }

    public static void reload(Consumer<Component> reply) {
        RecipeSyncController.reloadLocalRecipes();
        reply.accept(Component.literal("Re-reading local recipes...").withStyle(ChatFormatting.GOLD));
    }

    public static void dumpTypes(Consumer<Component> reply) {
        Map<RecipeType<?>, List<RecipeHolder<?>>> byType = RecipeSyncController.localRecipes().values().stream()
                .collect(Collectors.groupingBy(holder -> holder.value().getType()));

        reply.accept(Component.literal("Locally read recipes:").withStyle(ChatFormatting.GOLD));
        byType.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<RecipeType<?>, List<RecipeHolder<?>>> entry) -> entry.getValue().size()).reversed())
                .limit(20)
                .forEach(entry -> reply.accept(Component.literal("  " + typeName(entry.getKey()) + ": " + entry.getValue().size())));
    }

    public static int dumpRecipes(ResourceLocation typeId, Consumer<Component> reply) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            reply.accept(Component.literal("Unknown recipe type " + typeId).withStyle(ChatFormatting.RED));
            return 0;
        }

        List<RecipeHolder<?>> recipes = RecipeSyncController.localRecipes().values().stream()
                .filter(holder -> holder.value().getType() == type)
                .limit(MAX_LISTED)
                .toList();

        reply.accept(Component.literal("Locally read " + typeName(type) + " recipes (" + recipes.size() + " shown):")
                .withStyle(ChatFormatting.GOLD));
        recipes.forEach(holder -> reply.accept(Component.literal("  " + holder.id().location())));
        return recipes.size();
    }

    private static String typeNames(Collection<RecipeType<?>> types) {
        return types.stream().map(UseLocalRecipesCommand::typeName).sorted().collect(Collectors.joining(", "));
    }

    private static String typeName(RecipeType<?> type) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id != null ? id.toString() : type.toString();
    }
}
