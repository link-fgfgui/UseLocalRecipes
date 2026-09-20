package com.example.uselocalrecipes;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.Services;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point. The mod only does something on the client, so the entry point is client only.
 */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class UseLocalRecipesNeoForge {

    public UseLocalRecipesNeoForge(IEventBus modEventBus) {
        UseLocalRecipesConfig.load();
        Services.RECIPES.registerClientLifecycle();
    }
}
