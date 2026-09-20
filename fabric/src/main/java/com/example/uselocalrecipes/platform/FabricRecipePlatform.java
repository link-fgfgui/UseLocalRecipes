package com.example.uselocalrecipes.platform;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.platform.services.IRecipePlatform;
import com.example.uselocalrecipes.recipe.RecipeMerger;
import com.example.uselocalrecipes.runtime.RecipeSyncController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.fabricmc.fabric.impl.recipe.sync.SynchronizedRecipesImpl;
import net.fabricmc.fabric.impl.recipe.sync.client.SynchronizedClientRecipesSetter;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

public class FabricRecipePlatform implements IRecipePlatform {

    @Override
    public List<PackResources> createModDataPacks() {
        List<PackResources> packs = new ArrayList<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (Constants.MOD_ID.equals(modId) || "fabricloader".equals(modId) || "java".equals(modId) || "minecraft".equals(modId)) {
                // The vanilla data comes from the game itself, see LocalRecipeLoader.
                continue;
            }

            for (Path root : mod.getRootPaths()) {
                PackLocationInfo location = new PackLocationInfo(
                        "mod:" + modId, Component.literal(mod.getMetadata().getName()), PackSource.BUILT_IN, Optional.empty());
                packs.add(new PathPackResources(location, root));
            }
        }

        return packs;
    }

    @Override
    public void injectRecipes(Set<RecipeType<?>> recipeTypes, RecipeMap recipes) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            Constants.LOG.warn("Not connected, cannot hand over {} recipes", recipes.values().size());
            return;
        }

        List<RecipeHolder<?>> holders = List.copyOf(recipes.values());
        SynchronizedRecipes synchronizedRecipes = SynchronizedRecipesImpl.of(holders);

        // Fabric API has no public way to set the synchronized recipes yet, this is exactly what its own
        // recipe sync packet handler does before firing the event below.
        ((SynchronizedClientRecipesSetter) connection.recipes()).fabric_setSynchronizedClientRecipes(synchronizedRecipes);
        ClientRecipeSynchronizedEvent.EVENT.invoker().onRecipesSynchronized(client, synchronizedRecipes);
    }

    @Override
    public void registerClientLifecycle() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RecipeSyncController.onClientJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RecipeSyncController.onClientDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> RecipeSyncController.onClientTick());

        ClientRecipeSynchronizedEvent.EVENT.register((client, synchronizedRecipes) -> {
            RecipeMap recipes = RecipeMap.create(synchronizedRecipes.recipes());
            RecipeSyncController.onServerRecipesReceived(RecipeMerger.typesOf(recipes), recipes);
        });

        Constants.LOG.info("Use Local Recipes ready for Fabric");
    }
}
