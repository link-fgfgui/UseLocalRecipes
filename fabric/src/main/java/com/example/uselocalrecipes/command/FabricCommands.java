package com.example.uselocalrecipes.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Builds the client side {@code /ulr} command for Fabric. Fabric's client commands cannot share the tree
 * with NeoForge because they use their own command source type.
 */
public final class FabricCommands {

    private FabricCommands() {
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> create() {
        return ClientCommands.literal("ulr")
                .then(ClientCommands.literal("status").executes(context -> {
                    UseLocalRecipesCommand.status(reply(context));
                    return 1;
                }))
                .then(ClientCommands.literal("reload").executes(context -> {
                    UseLocalRecipesCommand.reload(reply(context));
                    return 1;
                }))
                .then(ClientCommands.literal("dump")
                        .executes(context -> {
                            UseLocalRecipesCommand.dumpTypes(reply(context));
                            return 1;
                        })
                        .then(ClientCommands.argument("type", IdentifierArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(UseLocalRecipesCommand.recipeTypeIds(), builder))
                                .executes(context -> UseLocalRecipesCommand.dumpRecipes(
                                        context.getArgument("type", Identifier.class), reply(context)))));
    }

    private static Consumer<Component> reply(CommandContext<FabricClientCommandSource> context) {
        return message -> context.getSource().sendFeedback(message);
    }
}
