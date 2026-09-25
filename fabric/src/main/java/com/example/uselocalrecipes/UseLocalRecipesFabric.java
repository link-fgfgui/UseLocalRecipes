package com.example.uselocalrecipes;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.Services;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point, the mod is client only in fabric.mod.json. */
public class UseLocalRecipesFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        UseLocalRecipesConfig.load();
        Services.RECIPES.registerClientLifecycle();
    }
}
