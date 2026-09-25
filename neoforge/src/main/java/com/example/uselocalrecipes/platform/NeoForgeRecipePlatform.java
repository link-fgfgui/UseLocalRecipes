package com.example.uselocalrecipes.platform;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.platform.services.IRecipePlatform;
import com.example.uselocalrecipes.runtime.RecipeSyncController;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

public class NeoForgeRecipePlatform implements IRecipePlatform {

    @Override
    public List<PackResources> createModDataPacks() {
        List<PackResources> packs = new ArrayList<>();

        for (IModFileInfo modFile : ModList.get().getModFiles()) {
            List<IModInfo> mods = modFile.getMods();
            if (mods.isEmpty()) {
                continue;
            }

            IModInfo first = mods.getFirst();
            if ("minecraft".equals(first.getModId()) || Constants.MOD_ID.equals(first.getModId())) {
                // Vanilla data comes from the game itself, see LocalRecipeLoader.
                continue;
            }

            try {
                Pack.ResourcesSupplier supplier = ResourcePackLoader.createPackForMod(modFile);
                PackLocationInfo location = new PackLocationInfo(
                        "mod:" + first.getModId(), Component.literal(first.getDisplayName()), PackSource.BUILT_IN, Optional.empty());
                // 26.3 replaced openPrimary with a metadata/resource API, this NeoForge helper handles it.
                Pack pack = ResourcePackLoader.readWithOptionalMeta(
                        location, supplier, PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
                pack.open().forEach(packs::add);
            } catch (Exception e) {
                Constants.LOG.warn("Could not read the data files of {}", first.getModId(), e);
            }
        }

        return packs;
    }

    @Override
    public void injectRecipes(Set<RecipeType<?>> recipeTypes, RecipeMap recipes) {
        // The same event NeoForge fires for server recipes, and the one viewers listen to.
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            boolean integratedServer = mc.getConnection() != null && mc.getConnection().getConnection().isMemoryConnection();
            try {
                // 26.2 split the event by cause, the client fires the packet one.
                NeoForge.EVENT_BUS.post(new TagsUpdatedEvent.ClientPacketReceived(mc.level.registryAccess(), integratedServer));
            } catch (Throwable t) {
                Constants.LOG.warn("Failed to fire TagsUpdatedEvent", t);
            }
        }
        NeoForge.EVENT_BUS.post(new RecipesReceivedEvent(recipeTypes, recipes));
    }

    @Override
    public void registerClientLifecycle() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> RecipeSyncController.onClientJoin());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> RecipeSyncController.onClientDisconnect());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> RecipeSyncController.onClientTick());
        NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent event) ->
                RecipeSyncController.onServerRecipesReceived(event.getRecipeTypes(), event.getRecipeMap()));

        Constants.LOG.info("Use Local Recipes ready for NeoForge");
    }
}
