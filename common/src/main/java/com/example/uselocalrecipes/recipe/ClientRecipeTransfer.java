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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Moves the ingredients of a recipe into a crafting container by simulating the clicks a player would do.
 *
 * <p>JEI normally asks the server to do the transfer, which only works when JEI is installed on the server
 * as well. This class is the fallback for that case and works the same way EMI's recipe transfer does:
 * the client decides where every item goes, and the server only sees ordinary slot clicks.
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
            // Move everything that is already in the crafting slots into the inventory first, so the recipe
            // can be placed into empty slots without having to merge with leftovers of a previous recipe.
            for (Slot slot : craftingSlots) {
                if (!slot.getItem().isEmpty()) {
                    click(gameMode, container, player, slot.index, 0, ClickType.QUICK_MOVE);
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

        int batches = maxTransfer ? countBatches(operations.results, available) : 1;
        if (!execute(gameMode, container, player, operations.results, batches, inventorySlots)) {
            return handlerHelper.createInternalError();
        }

        return null;
    }

    private static Map<Slot, ItemStack> collectAvailable(Player player, List<Slot> craftingSlots, List<Slot> inventorySlots, boolean afterClearing) {
        Map<Slot, ItemStack> available = new HashMap<>();
        if (!afterClearing) {
            // A preview has to assume that the crafting slots are usable as a source, the real transfer
            // moves their contents into the inventory first and can use them from there.
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
     * Every transfer operation moves exactly one item out of its source slot, so the amount of complete
     * sets that can be prepared is limited by the smallest source stack.
     */
    private static int countBatches(List<TransferOperation> operations, Map<Slot, ItemStack> available) {
        Map<Integer, Integer> required = new HashMap<>();
        Map<Integer, Integer> present = new HashMap<>();
        for (Slot slot : available.keySet()) {
            present.put(slot.index, available.get(slot).getCount());
        }
        for (TransferOperation operation : operations) {
            required.merge(operation.inventorySlotId(), 1, Integer::sum);
        }

        int batches = MAX_BATCHES;
        for (Map.Entry<Integer, Integer> entry : required.entrySet()) {
            batches = Math.min(batches, present.getOrDefault(entry.getKey(), 0) / entry.getValue());
        }
        return Math.max(1, batches);
    }

    private static boolean execute(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                                   List<TransferOperation> operations, int batches, List<Slot> inventorySlots) {
        for (TransferOperation operation : operations) {
            Slot source = container.getSlot(operation.inventorySlotId());
            Slot target = container.getSlot(operation.craftingSlotId());
            if (source == null || target == null) {
                return false;
            }

            int remaining = batches;

            // Pick the whole stack up, then place one item per set. Anything that is left over stays on
            // the cursor and is put back afterwards.
            click(gameMode, container, player, source.index, 0, ClickType.PICKUP);
            while (remaining > 0 && !container.getCarried().isEmpty()) {
                click(gameMode, container, player, target.index, 1, ClickType.PICKUP);
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

    /**
     * Puts the stack that is left on the cursor back into an empty slot.
     */
    private static boolean returnLeftovers(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                                           Slot source, List<Slot> inventorySlots) {
        if (source.getItem().isEmpty()) {
            click(gameMode, container, player, source.index, 0, ClickType.PICKUP);
            if (container.getCarried().isEmpty()) {
                return true;
            }
        }
        for (Slot slot : inventorySlots) {
            if (slot.getItem().isEmpty()) {
                click(gameMode, container, player, slot.index, 0, ClickType.PICKUP);
                if (container.getCarried().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void click(MultiPlayerGameMode gameMode, AbstractContainerMenu container, Player player,
                              int slot, int button, ClickType type) {
        gameMode.handleInventoryMouseClick(container.containerId, slot, button, type, player);
    }
}
