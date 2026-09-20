package com.example.uselocalrecipes.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;

/**
 * Builds the client side {@code /ulr} command for NeoForge.
 */
public final class NeoForgeCommands {

    private NeoForgeCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("ulr")
                .then(Commands.literal("status").executes(context -> {
                    UseLocalRecipesCommand.status(reply(context));
                    return 1;
                }))
                .then(Commands.literal("reload").executes(context -> {
                    UseLocalRecipesCommand.reload(reply(context));
                    return 1;
                }))
                .then(Commands.literal("dump")
                        .executes(context -> {
                            UseLocalRecipesCommand.dumpTypes(reply(context));
                            return 1;
                        })
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(UseLocalRecipesCommand.recipeTypeIds(), builder))
                                .executes(context -> UseLocalRecipesCommand.dumpRecipes(
                                        ResourceLocationArgument.getId(context, "type"), reply(context)))));
    }

    private static Consumer<Component> reply(CommandContext<CommandSourceStack> context) {
        return message -> context.getSource().sendSuccess(() -> message, false);
    }
}
