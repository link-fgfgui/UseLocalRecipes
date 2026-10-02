package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.recipe.ClientRecipeTransfer;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.common.Internal;
import mezz.jei.library.transfer.PlayerRecipeTransferHandler;
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
 * The 2x2 player crafting grid variant of {@link BasicRecipeTransferHandlerMixin}, also harmless without JEI.
 *
 * <p>JEI 27 on 1.21.11 calls {@code transferRecipe(IRecipeTransferContext, boolean)}, so that is the overload
 * the transfer has to be taken over in.
 */
@Pseudo
@Mixin(PlayerRecipeTransferHandler.class)
public class PlayerRecipeTransferHandlerMixin {

    /** Indexes of the recipe inputs that fit into the player grid after trimming the right and bottom edges, same as JEI. */
    private static final List<Integer> PLAYER_GRID_INDEXES = List.of(0, 1, 3, 4);

    @Shadow
    @Final
    private IRecipeTransferHandlerHelper handlerHelper;

    @Inject(
            method = "transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferContext;Z)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true)
    private void ulr$clientSideTransfer(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, InventoryMenu> context,
                                        boolean doTransfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (handlerHelper.recipeTransferHasServerSupport()) {
            return;
        }
        if (!UseLocalRecipesConfig.get().clientSideTransfer) {
            return;
        }

        List<IRecipeSlotView> inputViews = context.getRecipeSlots().getSlotViews(RecipeIngredientRole.INPUT);
        if (inputViews.size() <= PLAYER_GRID_INDEXES.getLast()) {
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

        InventoryMenu container = context.getContainer();
        RecipeHolder<CraftingRecipe> recipe = context.getRecipe();
        IRecipeTransferInfo<InventoryMenu, RecipeHolder<CraftingRecipe>> info = handlerHelper.createBasicRecipeTransferInfo(
                InventoryMenu.class, null, RecipeTypes.CRAFTING, 1, 4, 9, 36);
        List<Slot> craftingSlots = info.getRecipeSlots(container, recipe);
        List<Slot> inventorySlots = info.getInventorySlots(container, recipe);

        IRecipeTransferError error = ClientRecipeTransfer.transfer(
                container, gridView, context.getPlayer(), context.isMaxTransfer(), doTransfer,
                craftingSlots, inventorySlots, stackHelper, handlerHelper);
        if (error == null && doTransfer) {
            context.completeRecipeTransfer(RecipeTransferResult.SUCCESS);
        }
        cir.setReturnValue(error);
    }

    private static IStackHelper ulr$stackHelper() {
        return Internal.getOptionalJeiRuntime()
                .map(runtime -> runtime.getJeiHelpers().getStackHelper())
                .orElse(null);
    }
}
