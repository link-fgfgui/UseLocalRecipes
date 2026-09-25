package com.example.uselocalrecipes.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.common.transfer.RecipeTransferOperationsResult;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.common.transfer.TransferOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Moves the ingredients of a recipe into a crafting container by simulating the clicks a player would do.
 *
 * <p>JEI normally has the server do the transfer, which needs JEI on the server as well. This fallback
 * works like EMI's recipe transfer: the client decides where every item goes, the server only sees clicks.
 */
public final class ClientRecipeTransfer {

    /** Upper bound for the amount of sets a single transfer may prepare. */
    private static final int MAX_BATCHES = 64;

    private ClientRecipeTransfer() {
    }

    /**
     * @param doTransfer false to only check whether the transfer would work, used to preview the transfer button
     * @return null when the transfer works (or would work), otherwise the error to show to the player
     */
    public static IRecipeTransferError transfer(
            AbstractContainerMenu container,
            IRecipeSlotsView recipeSlotsView,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            IStackHelper stackHelper,
            IRecipeTransferHandlerHelper handlerHelper) {

        Minecraft client = Minecraft.getInstance();
        MultiPlayerGameMode gameMode = client.gameMode;
        if (gameMode == null) {
            return handlerHelper.createInternalError();
        }

        if (!container.getCarried().isEmpty()) {
            // Clicking with something on the cursor would swap instead of move items around.
            return handlerHelper.createInternalError();
        }

        List<IRecipeSlotView> inputViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputViews.isEmpty() || inputViews.size() > craftingSlots.size()) {
            return handlerHelper.createInternalError();
        }

        int filledCraftSlots = 0;
        int emptyInventorySlots = 0;
        for (Slot slot : craftingSlots) {
            if (!slot.getItem().isEmpty()) {
                if (!slot.allowModification(player)) {
                    return handlerHelper.createInternalError();
                }
                filledCraftSlots++;
            }
        }
        for (Slot slot : inventorySlots) {
            if (slot.getItem().isEmpty() && slot.allowModification(player)) {
                emptyInventorySlots++;
            }
        }

        // Enough room to shuffle the crafting slots around?
        int inputCount = (int) inputViews.stream().filter(view -> !view.isEmpty()).count();
        if (filledCraftSlots - inputCount > emptyInventorySlots) {
            return handlerHelper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full"));
        }

        if (doTransfer) {
            // Empty the crafting slots first, so the recipe can be placed without merging into leftovers.
            for (Slot slot : craftingSlots) {
                if (!slot.getItem().isEmpty()) {
                    click(gameMode, container, player, slot.index, 0, ContainerInput.QUICK_MOVE);
                }
            }
            for (Slot slot : craftingSlots) {
                if (!slot.getItem().isEmpty()) {
                    return handlerHelper.createInternalError();
                }
            }
        }

        Map<Slot, ItemStack> available = collectAvailable(player, craftingSlots, inventorySlots, doTransfer);
        RecipeTransferOperationsResult operations = RecipeTransferUtil.getRecipeTransferOperations(
                stackHelper, available, inputViews, craftingSlots);

        if (!operations.missingItems.isEmpty()) {
            return handlerHelper.createUserErrorForMissingSlots(
                    Component.translatable("jei.tooltip.error.recipe.transfer.missing"), operations.missingItems);
        }
        if (!RecipeTransferUtil.validateSlots(player, operations.results, craftingSlots, inventorySlots)) {
            return handlerHelper.createInternalError();
        }

        if (!doTransfer) {
            return null;
        }

        int batches = maxTransfer ? countBatches(container, operations.results, available) : 1;
        if (!execute(gameMode, container, player, operations.results, batches, inventorySlots)) {
            return handlerHelper.createInternalError();
        }

        return null;
    }

    private static Map<Slot, ItemStack> collectAvailable(Player player, List<Slot> craftingSlots, List<Slot> inventorySlots, boolean afterClearing) {
        Map<Slot, ItemStack> available = new HashMap<>();
        if (!afterClearing) {
            // A preview assumes the crafting slots are a source, the real transfer moves them to the inventory first.
            for (Slot slot : craftingSlots) {
                if (!slot.getItem().isEmpty() && slot.allowModification(player)) {
                    available.put(slot, slot.getItem().copy());
                }
            }
        }
        for (Slot slot : inventorySlots) {
            if (!slot.getItem().isEmpty() && slot.allowModification(player)) {
                available.put(slot, slot.getItem().copy());
            }
        }
        return available;
    }

    /**
     * A set takes {@link TransferOperation#count()} items from the source of each operation, so the amount
     * of complete sets is limited by the smallest source stack and by the capacity of the crafting slots.
     */
    private static int countBatches(AbstractContainerMenu container, List<TransferOperation> operations, Map<Slot, ItemStack> available) {
        Map<Slot, Integer> takenPerSet = new HashMap<>();
        Map<Slot, Integer> placedPerSet = new HashMap<>();
        Map<Slot, ItemStack> itemPerTarget = new HashMap<>();
        for (TransferOperation operation : operations) {
            Slot source = container.getSlot(operation.inventorySlotId());
            Slot target = container.getSlot(operation.craftingSlotId());
            takenPerSet.merge(source, operation.count(), Integer::sum);
            placedPerSet.merge(target, operation.count(), Integer::sum);
            itemPerTarget.putIfAbsent(target, available.get(source));
        }

        int batches = MAX_BATCHES;
        for (Map.Entry<Slot, Integer> entry : takenPerSet.entrySet()) {
            batches = Math.min(batches, available.get(entry.getKey()).getCount() / entry.getValue());
        }
        for (Map.Entry<Slot, Integer> entry : placedPerSet.entrySet()) {
            batches = Math.min(batches, entry.getKey().getMaxStackSize(itemPerTarget.get(entry.getKey())) / entry.getValue());
        }
        return Math.max(1, batches);
    }

    private static boolean execute(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                                   List<TransferOperation> operations, int batches, List<Slot> inventorySlots) {
        for (TransferOperation operation : operations) {
            Slot source = container.getSlot(operation.inventorySlotId());
            Slot target = container.getSlot(operation.craftingSlotId());

            // Every set needs this operation's count, one right click places one item.
            int remaining = batches * operation.count();

            // Pick the stack up, then place item by item; leftovers stay on the cursor and are put back below.
            click(gameMode, container, player, source.index, 0, ContainerInput.PICKUP);
            while (remaining > 0 && !container.getCarried().isEmpty()) {
                click(gameMode, container, player, target.index, 1, ContainerInput.PICKUP);
                remaining--;
            }
            if (remaining > 0) {
                return false;
            }

            if (!container.getCarried().isEmpty() && !returnLeftovers(gameMode, container, player, source, inventorySlots)) {
                return false;
            }
        }

        return container.getCarried().isEmpty();
    }

    /** Puts the stack that is left on the cursor back into an empty slot. */
    private static boolean returnLeftovers(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                                           Slot source, List<Slot> inventorySlots) {
        if (source.getItem().isEmpty()) {
            click(gameMode, container, player, source.index, 0, ContainerInput.PICKUP);
            if (container.getCarried().isEmpty()) {
                return true;
            }
        }
        for (Slot slot : inventorySlots) {
            if (slot.getItem().isEmpty()) {
                click(gameMode, container, player, slot.index, 0, ContainerInput.PICKUP);
                if (container.getCarried().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void click(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                              int slot, int button, ContainerInput type) {
        gameMode.handleContainerInput(container.containerId, slot, button, type, player);
    }
}
