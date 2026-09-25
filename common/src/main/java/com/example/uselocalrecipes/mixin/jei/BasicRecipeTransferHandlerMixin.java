package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.recipe.ClientRecipeTransfer;
import java.util.List;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.library.transfer.BasicRecipeTransferHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes over recipe transfer when JEI is not installed on the server, where JEI's own transfer would only
 * show a "no server" error. The transfer is then done with client side clicks, the way EMI moves items.
 *
 * <p>JEI's GUI calls {@code transferRecipe(IRecipeTransferContext, boolean)} since 30.30.0, so that is the
 * overload the transfer has to be taken over in.
 *
 * <p>{@link Pseudo} keeps this mixin harmless when JEI is not installed.
 */
@Pseudo
@Mixin(BasicRecipeTransferHandler.class)
public class BasicRecipeTransferHandlerMixin {

    @Shadow
    @Final
    private IConnectionToServer serverConnection;

    @Shadow
    @Final
    private IStackHelper stackHelper;

    @Shadow
    @Final
    private IRecipeTransferHandlerHelper handlerHelper;

    @Shadow
    @Final
    private IRecipeTransferInfo<AbstractContainerMenu, Object> transferInfo;

    @Inject(
            method = "transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferContext;Z)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true)
    private void ulr$clientSideTransfer(IRecipeTransferContext<?, ?> context, boolean doTransfer,
                                        CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (serverConnection.isJeiOnServer()) {
            // JEI is on the server, its own transfer is better than anything we could do here.
            return;
        }
        if (!UseLocalRecipesConfig.get().clientSideTransfer) {
            return;
        }

        AbstractContainerMenu container = context.getContainer();
        Object recipe = context.getRecipe();
        List<Slot> craftingSlots = transferInfo.getRecipeSlots(container, recipe);
        List<Slot> inventorySlots = transferInfo.getInventorySlots(container, recipe);
        if (!BasicRecipeTransferHandler.validateTransferInfo(transferInfo, container, craftingSlots, inventorySlots)) {
            return;
        }

        IRecipeTransferError error = ClientRecipeTransfer.transfer(
                container, context.getRecipeSlots(), context.getPlayer(), context.isMaxTransfer(), doTransfer,
                craftingSlots, inventorySlots, stackHelper, handlerHelper);
        if (error == null && doTransfer) {
            context.completeRecipeTransfer(RecipeTransferResult.SUCCESS);
        }
        cir.setReturnValue(error);
    }
}
