package com.example.uselocalrecipes;

import com.example.uselocalrecipes.command.FabricCommands;
import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Fabric entry point. This mod only does something on the client, so it is registered as a client
 * entry point and the mod is marked as client only in fabric.mod.json.
 */
public class UseLocalRecipesFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        UseLocalRecipesConfig.load();
        Services.RECIPES.registerClientLifecycle();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(FabricCommands.create()));
    }
}
