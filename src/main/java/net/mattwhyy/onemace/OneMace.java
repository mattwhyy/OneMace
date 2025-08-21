package net.mattwhyy.onemace;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OneMace extends JavaPlugin implements Listener {
    private boolean maceCrafted;
    private final NamespacedKey maceKey = new NamespacedKey(this, "mace-tracker");
    private final Set<UUID> trackedDestroyedItems = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        maceCrafted = getConfig().getBoolean("settings.mace-crafted", false);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!doesMaceExist()) {
                resetMaceCrafting(false);
                getConfig().set("offline_inventory", null);
                saveConfig();
            } else {
                removeAllMaceRecipes();
                getLogger().info("[OneMace] Mace already crafted. Recipes removed.");
            }
            }, 40L);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("onemace").setExecutor(new OneMaceCommand(this));
        getLogger().info("[OneMace] Plugin enabled!");
    }

    public void updateConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();

        if (!getConfig().contains("messages.crafted")) {
            getConfig().set("messages.crafted", "&b[OneMace] &eThe Mace has been crafted!");
        }
        if (!getConfig().contains("messages.lost")) {
            getConfig().set("messages.lost", "&b[OneMace] &eThe Mace has been lost!");
        }
        if (!getConfig().contains("settings.allow-mace-in-containers")) {
            getConfig().set("settings.allow-mace-in-containers", true);
        }

        saveConfig();
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean hasMace = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isMace(item)) {
                hasMace = true;
                break;
            }
        }

        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isMace(item)) {
                hasMace = true;
                getLogger().info("[OneMace] Player " + player.getName() + " logged out with the Mace in their Ender Chest.");
                break;
            }
        }

        getConfig().set("offline_inventory." + player.getUniqueId().toString(), hasMace);
        saveConfig();
    }


    @Override
    public void onDisable() {
        getLogger().info("[OneMace] Saving Ender Chest data before shutdown...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasMace = false;

            for (ItemStack item : player.getInventory().getContents()) {
                if (isMace(item)) {
                    hasMace = true;
                    break;
                }
            }

            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isMace(item)) {
                    hasMace = true;
                    getLogger().info("[OneMace] Player " + player.getName() + " had the Mace in their Ender Chest before shutdown.");
                    break;
                }
            }

            getConfig().set("offline_inventory." + player.getUniqueId().toString(), hasMace);
        }

        saveConfig();
        getLogger().info("[OneMace] Plugin disabled! Ender Chest data saved.");
    }

    public void removeAllMaceRecipes() {
        NamespacedKey vanillaMaceKey = NamespacedKey.minecraft("mace");
        if (Bukkit.getRecipe(vanillaMaceKey) != null) {
            Bukkit.removeRecipe(vanillaMaceKey);
            getLogger().info("[OneMace] Removed vanilla Mace recipe.");
        }
    }


    @EventHandler
    public void onMaceDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();
        if (isMace(droppedItem.getItemStack())) {
            saveMaceOwner(null);
            getLogger().info("[OneMace] Mace ownership cleared due to drop.");

            getConfig().set("offline_inventory." + player.getUniqueId().toString(), false);
            saveConfig();
        }
    }


    @EventHandler
    public void onMacePickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        ItemStack pickedItem = event.getItem().getItemStack();

        if (isMace(pickedItem)) {
            if (event.getEntity() instanceof Player) {
                saveMaceOwner(event.getEntity().getUniqueId());
            }
            else {
                saveMaceOwner(null);
            }
        }
    }

    @EventHandler
    public void onMaceMove(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack cursorItem = event.getCursor();
            ItemStack clickedItem = event.getCurrentItem();

            if (isMace(cursorItem)) {
                saveMaceOwner(player.getUniqueId());
                getConfig().set("offline_inventory." + player.getUniqueId().toString(), false);
                saveConfig();
            }
            else if (isMace(clickedItem) && (isStorageContainer(event.getInventory().getType()) || isAnimalStorage(event))) {
                if (getConfig().getBoolean("settings.allow-mace-in-containers", true)) {
                    saveMaceOwner(null);
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    private boolean isStorageContainer(org.bukkit.event.inventory.InventoryType type) {
        return type == org.bukkit.event.inventory.InventoryType.CHEST ||
                type == org.bukkit.event.inventory.InventoryType.BARREL ||
                type == org.bukkit.event.inventory.InventoryType.DROPPER ||
                type == org.bukkit.event.inventory.InventoryType.DISPENSER ||
                type == org.bukkit.event.inventory.InventoryType.SHULKER_BOX ||
                type == org.bukkit.event.inventory.InventoryType.HOPPER;
    }

    private boolean isAnimalStorage(org.bukkit.event.inventory.InventoryClickEvent event) {
        return event.getInventory().getHolder() instanceof org.bukkit.entity.AbstractHorse;
    }


    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        getConfig().set("offline_inventory." + playerUUID.toString(), null);
        saveConfig();

        if (!doesMaceExist()) {
            resetMaceCrafting(false);
        }
    }

    public void markMace(ItemStack mace) {
        ItemMeta meta = mace.getItemMeta();
        if (meta != null) {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(maceKey, PersistentDataType.STRING, "true");
            mace.setItemMeta(meta);
        }
    }

    public boolean isMace(ItemStack item) {
        try {
            if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            return data.has(maceKey, PersistentDataType.STRING);
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.MACE) {
            if (maceCrafted) {
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.MACE) {
            if (maceCrafted) {
                event.setCancelled(true);
                return;
            }
            maceCrafted = true;
            getConfig().set("settings.mace-crafted", true);
            saveMaceOwner(event.getWhoClicked().getUniqueId());
            saveConfig();

            ItemStack mace = event.getInventory().getResult();
            if (mace != null) {
                markMace(mace);
            }


            Bukkit.getScheduler().runTask(this, this::removeAllMaceRecipes);
            getLogger().info("[OneMace] Mace crafted! Removing recipe.");

            if (getConfig().getBoolean("settings.announce-mace-messages", true)) {
                String craftedMessage = getConfig().getString("messages.crafted", "&b[OneMace] The Mace has been crafted!");
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', craftedMessage));
            }
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getRecipe().getResult().getType() == Material.MACE) {
            event.setCancelled(true);
        }
    }

    private boolean doesMaceExist() {
        getLogger().info("[OneMace] Checking if Mace exists...");
        boolean maceFound = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isMace(item) || isMaceInsideShulker(item)) {
                    getLogger().info("[OneMace] Mace found in " + player.getName() + "'s inventory.");
                    return true;
                }
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isMace(item) || isMaceInsideShulker(item)) {
                    getLogger().info("[OneMace] Mace found in " + player.getName() + "'s Ender Chest.");
                    return true;
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && (isMace(item.getItemStack()) || isMaceInsideShulker(item.getItemStack()))) {
                    getLogger().info("[OneMace] Mace found as a dropped item in world: " + world.getName());
                    return true;
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        Inventory inv = container.getInventory();
                        for (ItemStack item : inv.getContents()) {
                            if (isMace(item) || isMaceInsideShulker(item)) {
                                getLogger().info("[OneMace] Mace found inside a container at " + state.getLocation());
                                return true;
                            }
                        }
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractHorse horse) {
                    for (ItemStack item : horse.getInventory().getContents()) {
                        if (isMace(item) || isMaceInsideShulker(item)) {
                            getLogger().info("[OneMace] Mace found in a horse inventory!");
                            return true;
                        }
                    }
                }
                if (entity instanceof StorageMinecart minecart) {
                    for (ItemStack item : minecart.getInventory().getContents()) {
                        if (isMace(item) || isMaceInsideShulker(item)) {
                            getLogger().info("[OneMace] Mace found in a storage minecart!");
                            return true;
                        }
                    }
                }
                if (entity instanceof ChestBoat chestBoat) {
                    for (ItemStack item : chestBoat.getInventory().getContents()) {
                        if (isMace(item) || isMaceInsideShulker(item)) {
                            getLogger().info("[OneMace] Mace found in a Chest Boat at " +
                                    "X: " + entity.getLocation().getBlockX() +
                                    " Y: " + entity.getLocation().getBlockY() +
                                    " Z: " + entity.getLocation().getBlockZ() +
                                    " in world " + entity.getWorld().getName());
                            return true;
                        }
                    }
                }
            }
        }

        if (getConfig().isConfigurationSection("offline_inventory")) {
            for (String uuid : getConfig().getConfigurationSection("offline_inventory").getKeys(true)) {
                if (getConfig().getBoolean("offline_inventory." + uuid, true)) {
                    getLogger().info("[OneMace] Mace is in an offline player's inventory (UUID: " + uuid + ").");
                    return true;
                }
            }
        }

        getLogger().info("[OneMace] Mace does not exist! Crafting can be re-enabled.");

        getConfig().set("offline_inventory", null);
        saveConfig();
        return false;
    }



    private boolean isMaceInsideShulker(ItemStack item) {
        if (item == null || item.getType() != Material.SHULKER_BOX) {
            return false;
        }

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta == null || !(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return false;
        }

        Inventory shulkerInv = shulkerBox.getInventory();
        for (ItemStack storedItem : shulkerInv.getContents()) {
            if (isMace(storedItem) || isMaceInsideShulker(storedItem)) {
                return true;
            }
        }
        return false;
    }



    public void saveMaceOwner(UUID ownerUUID) {
        if (ownerUUID == null) {
            getConfig().set("settings.mace-owner", null);
        } else {
            getConfig().set("settings.mace-owner", ownerUUID.toString());
        }
        saveConfig();
    }


    public UUID getMaceOwner() {
        String ownerUUID = getConfig().getString("settings.mace-owner");
        return (ownerUUID != null) ? UUID.fromString(ownerUUID) : null;
    }

    public boolean isMaceOwner(UUID playerUUID) {
        UUID maceOwner = getMaceOwner();
        return maceOwner != null && maceOwner.equals(playerUUID);
    }

    public void resetMaceCrafting(boolean announce) {
        maceCrafted = false;
        getConfig().set("settings.mace-crafted", false);
        getConfig().set("offline_inventory", null);
        saveConfig();

        addVanillaMaceRecipe();

        getLogger().info("[OneMace] No Mace found. Crafting is re-enabled.");

        if (announce && getConfig().getBoolean("settings.announce-mace-messages", true)) {
            String lostMessage = getConfig().getString("messages.lost", "&b[OneMace] The Mace has been lost!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', lostMessage));
        }
    }

    private void addVanillaMaceRecipe() {
        NamespacedKey vanillaMaceKey = NamespacedKey.minecraft("mace");

        if (Bukkit.getRecipe(vanillaMaceKey) == null) {
            Bukkit.reloadData();
            getLogger().info("[OneMace] Vanilla Mace recipe has been restored.");
        }
    }

    @EventHandler
    public void onMaceBreak(PlayerItemBreakEvent event) {
        ItemStack brokenItem = event.getBrokenItem();
        if (isMace(brokenItem)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!doesMaceExist()) {
                    resetMaceCrafting(true);
                }
            }, 50L);
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        if (isMace(event.getEntity().getItemStack())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!doesMaceExist()) {
                    resetMaceCrafting(true);
                }
            }, 50L);
        }
    }

    @EventHandler
    public void onItemRemoved(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Item item) {
            if (isMace(item.getItemStack())) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!doesMaceExist()) {
                        resetMaceCrafting(true);
                    }
                }, 50L);
            }
        }
    }

    @EventHandler
    public void onBundleStore(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack cursorItem = event.getCursor();
        ItemStack clickedItem = event.getCurrentItem();
        if (cursorItem == null || clickedItem == null) {
            return;
        }

        if (clickedInventory != null && clickedInventory.getType() == InventoryType.SHULKER_BOX || clickedInventory.getType() == InventoryType.HOPPER) {
            if (isMace(cursorItem) || isMace(clickedItem)) {
                event.setCancelled(true);
            }
        }

        if (event.isShiftClick() && isMace(clickedItem)) {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getType() == InventoryType.SHULKER_BOX || topInventory.getType() == InventoryType.HOPPER){
                event.setCancelled(true);
            }
        }

        if ((isMace(cursorItem) && isBundle(clickedItem)) || (isBundle(cursorItem) && isMace(clickedItem))) {
            event.setCancelled(true);
        }

        if (event.isShiftClick() && isMace(clickedItem)) {
            if (event.getInventory().getType() == InventoryType.PLAYER) {
                for (ItemStack item : event.getWhoClicked().getInventory().getContents()) {
                    if (isBundle(item)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();

        if (isMace(item)) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        Item item = event.getItem();

        if (isMace(item.getItemStack())) {
            event.setCancelled(true);
        }
    }


    private boolean isBundle(ItemStack item) {
        if (item == null) return false;

        Material type = item.getType();
        return type == Material.BUNDLE ||
                type.name().endsWith("_BUNDLE");
    }
}
