package com.example.uselocalrecipes.mixin;

import java.util.Map;
import net.minecraft.client.multiplayer.ClientRecipeContainer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientRecipeContainer.class)
public interface ClientRecipeContainerAccessor {

    @Accessor("itemSets")
    Map<ResourceKey<RecipePropertySet>, RecipePropertySet> uselocalrecipes$getItemSets();
}
