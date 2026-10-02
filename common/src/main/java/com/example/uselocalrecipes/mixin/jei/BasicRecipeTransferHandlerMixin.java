package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.config.UseLocalRecipesConfig;
import com.example.uselocalrecipes.recipe.ClientRecipeTransfer;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.library.transfer.BasicRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
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
 * <p>JEI 26.3 on 1.21.10 calls the six argument {@code transferRecipe} overload, which is the one taken
 * over here. The context based overload only exists in newer JEI builds.
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
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            cancellable = true)
    private void ulr$clientSideTransfer(AbstractContainerMenu container, Object recipe, IRecipeSlotsView recipeSlotsView,
                                        Player player, boolean maxTransfer, boolean doTransfer,
                                        CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (serverConnection.isJeiOnServer()) {
            // JEI is on the server, its own transfer is better than anything we could do here.
            return;
        }
        if (!UseLocalRecipesConfig.get().clientSideTransfer) {
            return;
        }

        List<Slot> craftingSlots = transferInfo.getRecipeSlots(container, recipe);
        List<Slot> inventorySlots = transferInfo.getInventorySlots(container, recipe);
        if (!BasicRecipeTransferHandler.validateTransferInfo(transferInfo, container, craftingSlots, inventorySlots)) {
            return;
        }

        cir.setReturnValue(ClientRecipeTransfer.transfer(
                container, recipeSlotsView, player, maxTransfer, doTransfer,
                craftingSlots, inventorySlots, stackHelper, handlerHelper));
    }
}
