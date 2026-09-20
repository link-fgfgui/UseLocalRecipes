package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.recipe.ClientRecipeTransfer;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.common.Internal;
import mezz.jei.library.transfer.PlayerRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 2x2 player crafting grid variant of {@link BasicRecipeTransferHandlerMixin}.
 *
 * <p>{@link Pseudo} keeps this mixin harmless when JEI is not installed.
 */
@Pseudo
@Mixin(PlayerRecipeTransferHandler.class)
public class PlayerRecipeTransferHandlerMixin {

    /**
     * Indexes of the crafting recipe inputs that fit into the player crafting grid when the right and
     * bottom edges are trimmed, the same ones JEI uses.
     */
    private static final List<Integer> PLAYER_GRID_INDEXES = List.of(0, 1, 3, 4);

    @Shadow
    @Final
    private IRecipeTransferHandlerHelper handlerHelper;

    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/InventoryMenu;Lnet/minecraft/world/item/crafting/RecipeHolder;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true)
    private void ulr$clientSideTransfer(InventoryMenu container, RecipeHolder<CraftingRecipe> recipe,
                                        IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer,
                                        boolean doTransfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (handlerHelper.recipeTransferHasServerSupport()) {
            return;
        }
        if (!UseLocalRecipesConfig.get().clientSideTransfer) {
            return;
        }

        List<IRecipeSlotView> inputViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputViews.size() <= PLAYER_GRID_INDEXES.get(PLAYER_GRID_INDEXES.size() - 1)) {
            return;
        }
        for (int i = 0; i < inputViews.size(); i++) {
            if (!PLAYER_GRID_INDEXES.contains(i) && !inputViews.get(i).isEmpty()) {
                // Too large for the player grid, JEI reports that on its own.
                return;
            }
        }

        IStackHelper stackHelper = ulr$stackHelper();
        if (stackHelper == null) {
            return;
        }

        List<IRecipeSlotView> gridInputs = PLAYER_GRID_INDEXES.stream().map(inputViews::get).toList();
        IRecipeSlotsView gridView = handlerHelper.createRecipeSlotsView(gridInputs);

        IRecipeTransferInfo<InventoryMenu, RecipeHolder<CraftingRecipe>> info = handlerHelper.createBasicRecipeTransferInfo(
                InventoryMenu.class, null, RecipeTypes.CRAFTING, 1, 4, 9, 36);
        List<Slot> craftingSlots = info.getRecipeSlots(container, recipe);
        List<Slot> inventorySlots = info.getInventorySlots(container, recipe);

        cir.setReturnValue(ClientRecipeTransfer.transfer(
                container, gridView, player, maxTransfer, doTransfer,
                craftingSlots, inventorySlots, stackHelper, handlerHelper));
    }

    private static IStackHelper ulr$stackHelper() {
        try {
            return Internal.getJeiRuntime().getJeiHelpers().getStackHelper();
        } catch (Throwable t) {
            return null;
        }
    }
}
