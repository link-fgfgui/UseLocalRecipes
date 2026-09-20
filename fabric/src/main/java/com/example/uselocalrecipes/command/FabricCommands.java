package com.example.uselocalrecipes.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds the client side {@code /ulr} command for Fabric. Fabric's client commands cannot share the tree
 * with NeoForge because they use their own command source type.
 */
public final class FabricCommands {

    private FabricCommands() {
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> create() {
        return ClientCommandManager.literal("ulr")
                .then(ClientCommandManager.literal("status").executes(context -> {
                    UseLocalRecipesCommand.status(reply(context));
                    return 1;
                }))
                .then(ClientCommandManager.literal("reload").executes(context -> {
                    UseLocalRecipesCommand.reload(reply(context));
                    return 1;
                }))
                .then(ClientCommandManager.literal("dump")
                        .executes(context -> {
                            UseLocalRecipesCommand.dumpTypes(reply(context));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("type", ResourceLocationArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(UseLocalRecipesCommand.recipeTypeIds(), builder))
                                .executes(context -> UseLocalRecipesCommand.dumpRecipes(
                                        context.getArgument("type", ResourceLocation.class), reply(context)))));
    }

    private static Consumer<Component> reply(CommandContext<FabricClientCommandSource> context) {
        return message -> context.getSource().sendFeedback(message);
    }
}
