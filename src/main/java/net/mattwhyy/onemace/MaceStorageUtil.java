package net.mattwhyy.onemace;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class MaceStorageUtil {
    private static final int MAX_NESTING_DEPTH = 16;

    private MaceStorageUtil() {
    }

    static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE && item.getAmount() > 0;
    }

    static boolean isBundle(ItemStack item) {
        return item != null && item.getItemMeta() instanceof BundleMeta;
    }

    static boolean isShelf(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.equals("SHELF") || name.endsWith("_SHELF");
    }

    static boolean containsMace(Inventory inventory) {
        if (inventory == null) return false;
        for (ItemStack item : inventory.getContents()) {
            if (containsMace(item)) return true;
        }
        return false;
    }

    static boolean containsMace(ItemStack item) {
        return containsMace(item, 0);
    }

    private static boolean containsMace(ItemStack item, int depth) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return false;
        if (isMace(item)) return true;
        if (depth >= MAX_NESTING_DEPTH) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            for (ItemStack stored : bundleMeta.getItems()) {
                if (containsMace(stored, depth + 1)) return true;
            }
        }

        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof InventoryHolder holder) {
                for (ItemStack stored : holder.getInventory().getContents()) {
                    if (containsMace(stored, depth + 1)) return true;
                }
            }
        }

        return false;
    }

    static void removeExtraMaces(Inventory inventory, CleanupState state) {
        removeExtraMaces(inventory, state, 0);
    }

    static ItemStack removeExtraMaces(ItemStack item, CleanupState state) {
        return removeExtraMaces(item, state, 0);
    }

    private static void removeExtraMaces(Inventory inventory, CleanupState state, int depth) {
        if (inventory == null || depth > MAX_NESTING_DEPTH) return;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack cleaned = removeExtraMaces(item, state, depth);
            if (cleaned == null || cleaned.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, cleaned);
            }
        }
    }

    private static ItemStack removeExtraMaces(ItemStack item, CleanupState state, int depth) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return item;

        if (isMace(item)) {
            int amount = item.getAmount();
            if (!state.keptOne) {
                state.keptOne = true;
                if (amount > 1) {
                    state.removed += amount - 1;
                    item.setAmount(1);
                }
                return item;
            }

            state.removed += amount;
            return null;
        }

        if (depth >= MAX_NESTING_DEPTH) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            List<ItemStack> cleanedItems = new ArrayList<>();
            for (ItemStack stored : bundleMeta.getItems()) {
                ItemStack cleaned = removeExtraMaces(stored, state, depth + 1);
                if (cleaned != null && cleaned.getAmount() > 0) {
                    cleanedItems.add(cleaned);
                }
            }
            bundleMeta.setItems(cleanedItems);
            item.setItemMeta(bundleMeta);
            meta = item.getItemMeta();
        }

        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof InventoryHolder holder) {
                removeExtraMaces(holder.getInventory(), state, depth + 1);
                blockStateMeta.setBlockState(blockState);
                item.setItemMeta(blockStateMeta);
            }
        }

        return item;
    }

    static final class CleanupState {
        private boolean keptOne;
        private int removed;

        boolean keptOne() {
            return keptOne;
        }

        int removed() {
            return removed;
        }
    }
}
