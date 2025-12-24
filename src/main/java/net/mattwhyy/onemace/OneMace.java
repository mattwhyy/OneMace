package net.mattwhyy.onemace;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class OneMace extends JavaPlugin implements Listener {
    private boolean maceCrafted;
    private final NamespacedKey maceKey = new NamespacedKey(this, "mace-tracker");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        loadAllowedContainers();
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
        getCommand("onemace").setTabCompleter(new OneMaceCommand(this));
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

        if (!getConfig().contains("settings.allow-locate-for-all")) {
            getConfig().set("settings.allow-locate-for-all", false);
        }
        if (!getConfig().contains("settings.colored-name")) {
            getConfig().set("settings.colored-name", false);
        }
        if (!getConfig().contains("settings.mace-name-color")) {
            getConfig().set("settings.mace-name-color", "RED");
        }

        if (!getConfig().contains("settings.optional-allowed-containers")) {
            getConfig().set("settings.optional-allowed-containers", Arrays.asList("ENDER_CHEST", "ANVIL", "ENCHANTING"));
        }

        saveConfig();
    }

    private final Set<InventoryType> mandatoryContainers = Set.of(
            InventoryType.PLAYER,
            InventoryType.CRAFTING,
            InventoryType.WORKBENCH,
            InventoryType.CREATIVE
    );

    private final Set<InventoryType> allowedContainers = new HashSet<>();

    public void loadAllowedContainers() {
        allowedContainers.clear();
        allowedContainers.addAll(mandatoryContainers);

        List<String> containerNames = getConfig().getStringList("settings.optional-allowed-containers");
        for (String name : containerNames) {
            try {
                allowedContainers.add(InventoryType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("[OneMace] Invalid optional inventory type in config: " + name);
            }
        }
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

        if (isMaceOwner(player.getUniqueId())) {
            saveMaceOwner(null);
        }

        getConfig().set("offline_inventory." + player.getUniqueId(), hasMace);
        saveConfig();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            if (isMace(drop)) {
                saveMaceOwner(null);
                break;
            }
        }
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

            getConfig().set("offline_inventory." + player.getUniqueId(), hasMace);
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

    private boolean isAllowedContainer(InventoryType type) {
        return allowedContainers.contains(type);
    }

    @EventHandler
    public void onMaceDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();
        if (isMace(droppedItem.getItemStack())) {
            saveMaceOwner(null);
            getLogger().info("[OneMace] Mace ownership cleared due to drop.");

            getConfig().set("offline_inventory." + player.getUniqueId(), false);
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
    public void onMaceMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        boolean cursorMace = isMace(cursor);
        boolean clickedMace = isMace(clicked);

        if (!cursorMace && !clickedMace) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (isMace(hotbarItem)) {
                    cursorMace = true;
                }
            }

            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = player.getInventory().getItemInOffHand();
                if (isMace(offhandItem)) {
                    cursorMace = true;
                }
            }
            if (!cursorMace && !clickedMace) return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        Inventory bottomInventory = event.getView().getBottomInventory();

        if (!isAllowedContainer(topInventory.getType()) && topInventory.getType() != InventoryType.CRAFTING) {
            boolean shouldCancel = false;

            switch (event.getClick()) {
                case LEFT:
                case RIGHT:
                case MIDDLE:
                case CREATIVE:
                    if (cursorMace && event.getClickedInventory() == topInventory) {
                        shouldCancel = true;
                    } else if (clickedMace && event.getClickedInventory() == topInventory) {
                        if (cursor == null || cursor.getType() == Material.AIR) {
                        } else {
                            shouldCancel = true;
                        }
                    }
                    break;

                case SHIFT_LEFT:
                case SHIFT_RIGHT:
                    if (clickedMace && event.getClickedInventory() == bottomInventory) {
                        shouldCancel = true;
                    } else if (clickedMace && event.getClickedInventory() == topInventory) {
                    }
                    break;

                case NUMBER_KEY:
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (isMace(hotbarItem)) {
                        if (event.getClickedInventory() == topInventory) {
                            shouldCancel = true;
                        }
                    } else if (clickedMace) {
                        if (event.getClickedInventory() == topInventory) {
                        }
                    }
                    break;

                case SWAP_OFFHAND:
                    ItemStack offhandItem = player.getInventory().getItemInOffHand();
                    if (isMace(offhandItem)) {
                        if (event.getClickedInventory() == topInventory) {
                            shouldCancel = true;
                        }
                    } else if (clickedMace) {
                        if (event.getClickedInventory() == topInventory) {
                        }
                    }
                    break;

                case DROP:
                case CONTROL_DROP:
                    if (clickedMace && event.getClickedInventory() == topInventory) {
                        shouldCancel = true;
                    }
                    break;

                case WINDOW_BORDER_LEFT:
                case WINDOW_BORDER_RIGHT:
                    shouldCancel = true;
                    break;

                default:
                    if (cursorMace || clickedMace) {
                        shouldCancel = true;
                    }
            }

            if (shouldCancel) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
        Inventory targetInv = null;

        switch (event.getClick()) {
            case NUMBER_KEY:
                if (isMace(player.getInventory().getItem(event.getHotbarButton()))) {
                    targetInv = event.getClickedInventory();
                } else if (clickedMace) {
                    targetInv = player.getInventory();
                }
                break;

            case SWAP_OFFHAND:
                if (isMace(player.getInventory().getItemInOffHand())) {
                    targetInv = event.getClickedInventory();
                } else if (clickedMace) {
                    targetInv = player.getInventory();
                }
                break;

            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                if (clickedMace) {
                    targetInv = (event.getClickedInventory() == event.getView().getTopInventory())
                            ? event.getView().getBottomInventory()
                            : event.getView().getTopInventory();
                }
                break;

            default:
                if (cursorMace) {
                    targetInv = event.getClickedInventory();
                } else if (clickedMace && (cursor == null || cursor.getType() == Material.AIR)) {
                }
        }

        if (targetInv != null) {
            if (targetInv.getType() == InventoryType.PLAYER) {
                saveMaceOwner(player.getUniqueId());
            } else if (isAllowedContainer(targetInv.getType())) {
                saveMaceOwner(null);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        getConfig().set("offline_inventory." + playerUUID, null);
        saveConfig();

        if (!doesMaceExist()) {
            resetMaceCrafting(false);
        }

        if (isMaceOwner(playerUUID)) {
            updateMaceNameColor(playerUUID);
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

    private void updateMaceNameColor(UUID ownerUUID) {
        if (!getConfig().getBoolean("settings.colored-name", true)) {
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team maceTeam = board.getTeam("maceHolder");
        if (maceTeam == null) {
            maceTeam = board.registerNewTeam("maceHolder");
        }

        maceTeam.getEntries().forEach(maceTeam::removeEntry);

        if (ownerUUID != null) {
            Player player = Bukkit.getPlayer(ownerUUID);
            if (player != null) {
                String colorName = getConfig().getString("settings.mace-name-color", "RED").toUpperCase();
                try {
                    ChatColor color = ChatColor.valueOf(colorName);
                    maceTeam.setColor(color);
                } catch (IllegalArgumentException e) {
                    maceTeam.setColor(ChatColor.RED);
                }

                maceTeam.addEntry(player.getName());
                getLogger().info("[OneMace] Applied colored name to mace holder " + player.getName());
            }
        }
    }

    public void saveMaceOwner(UUID ownerUUID) {
        if (ownerUUID == null) {
            getConfig().set("settings.mace-owner", null);
        } else {
            getConfig().set("settings.mace-owner", ownerUUID.toString());
        }
        saveConfig();

        updateMaceNameColor(ownerUUID);
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
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.MACE) return;

        if (block.getType() == Material.DECORATED_POT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.MACE) return;

        if (entity instanceof ItemFrame) {
            event.setCancelled(true);
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

    @EventHandler
    public void onBundleUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack usedItem = event.getItem();

        if (isMace(mainHand)) {
            if (isBundle(usedItem) || isBundle(offHand)) {
                event.setCancelled(true);
                player.updateInventory();
            }
        }
    }

    @EventHandler
    public void onBundleMaceInventoryMove(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isMace(cursor) && isBundle(current)) {
            event.setCancelled(true);
            player.updateInventory();
        }

        if (isMace(current) && isBundle(cursor)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }
}
