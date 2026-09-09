package net.mattwhyy.onemace;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.entity.RemovalReason;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class OneMace extends JavaPlugin implements Listener {
    private boolean maceCrafted;
    private boolean configDirty;
    private final NamespacedKey maceKey = new NamespacedKey(this, "mace-tracker");

    private final Set<InventoryType> mandatoryContainers = Set.of(
            InventoryType.PLAYER,
            InventoryType.CRAFTING,
            InventoryType.WORKBENCH,
            InventoryType.CREATIVE
    );
    private final Set<InventoryType> allowedContainers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        loadAllowedContainers();
        maceCrafted = getConfig().getBoolean("settings.mace-crafted", false);

        Bukkit.getPluginManager().registerEvents(this, this);
        OneMaceCommand command = new OneMaceCommand(this);
        Objects.requireNonNull(getCommand("onemace"), "onemace command is missing from plugin.yml").setExecutor(command);
        Objects.requireNonNull(getCommand("onemace"), "onemace command is missing from plugin.yml").setTabCompleter(command);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (maceCrafted) {
                removeAllMaceRecipes();
                getLogger().info("[OneMace] Mace is recorded as crafted. Recipes removed.");
            } else if (hasKnownMace()) {
                lockMaceCrafting();
                getLogger().info("[OneMace] Mace found. Recipes removed.");
            } else {
                ensureMaceRecipeAvailable();
            }
        }, 40L);

        getLogger().info("[OneMace] Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[OneMace] Saving offline Mace tracking before shutdown...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            getConfig().set("offline_inventory." + player.getUniqueId(), playerHasMace(player));
        }

        saveConfig();
        getLogger().info("[OneMace] Plugin disabled!");
    }

    public void markConfigDirty() {
        if (configDirty) return;
        configDirty = true;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            saveConfig();
            configDirty = false;
        }, 40L);
    }

    public void updateConfig() {
        boolean changed = false;

        if (!getConfig().contains("messages.crafted")) {
            getConfig().set("messages.crafted", "&b[OneMace] &eThe Mace has been crafted!");
            changed = true;
        }
        if (!getConfig().contains("messages.lost")) {
            getConfig().set("messages.lost", "&b[OneMace] &eThe Mace has been lost!");
            changed = true;
        }
        if (!getConfig().contains("settings.allow-locate-for-all")) {
            getConfig().set("settings.allow-locate-for-all", false);
            changed = true;
        }
        if (!getConfig().contains("settings.colored-name")) {
            getConfig().set("settings.colored-name", false);
            changed = true;
        }
        if (!getConfig().contains("settings.mace-name-color")) {
            getConfig().set("settings.mace-name-color", "RED");
            changed = true;
        }
        if (!getConfig().contains("settings.optional-allowed-containers")) {
            getConfig().set("settings.optional-allowed-containers", Arrays.asList("ENDER_CHEST", "ANVIL", "ENCHANTING"));
            changed = true;
        }

        if (changed) markConfigDirty();
    }

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

    private boolean isAllowedContainer(InventoryType type) {
        return allowedContainers.contains(type);
    }

    public boolean isMace(ItemStack item) {
        return MaceStorageUtil.isMace(item);
    }

    public boolean containsMace(ItemStack item) {
        return MaceStorageUtil.containsMace(item);
    }

    public boolean containsMace(Inventory inventory) {
        return MaceStorageUtil.containsMace(inventory);
    }

    public boolean isMaceCrafted() {
        return maceCrafted;
    }

    public boolean hasOfflineMaceRecord() {
        if (!getConfig().isConfigurationSection("offline_inventory")) return false;

        for (String uuid : getConfig().getConfigurationSection("offline_inventory").getKeys(false)) {
            if (getConfig().getBoolean("offline_inventory." + uuid, false)) return true;
        }
        return false;
    }

    public void markMace(ItemStack mace) {
        if (!isMace(mace)) return;

        ItemMeta meta = mace.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(maceKey, PersistentDataType.STRING, "true");
        mace.setItemMeta(meta);
    }

    public void lockMaceCrafting() {
        maceCrafted = true;
        getConfig().set("settings.mace-crafted", true);
        markConfigDirty();
        removeAllMaceRecipes();
    }

    public void removeAllMaceRecipes() {
        NamespacedKey vanillaMaceKey = NamespacedKey.minecraft("mace");
        if (Bukkit.getRecipe(vanillaMaceKey) != null) {
            Bukkit.removeRecipe(vanillaMaceKey);
        }

        for (int i = 0; i < 3; i++) {
            NamespacedKey key = new NamespacedKey(this, "mace-variant-" + i);
            if (Bukkit.getRecipe(key) != null) {
                Bukkit.removeRecipe(key);
            }
        }
    }

    public void resetMaceCrafting(boolean announce) {
        boolean wasCrafted = maceCrafted;

        maceCrafted = false;
        getConfig().set("settings.mace-crafted", false);
        getConfig().set("offline_inventory", null);
        markConfigDirty();
        ensureMaceRecipeAvailable();

        if (wasCrafted) {
            getLogger().info("[OneMace] No Mace found. Crafting is re-enabled.");
        }

        if (wasCrafted && announce && getConfig().getBoolean("settings.announce-mace-messages", true)) {
            String lostMessage = getConfig().getString("messages.lost", "&b[OneMace] The Mace has been lost!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', lostMessage));
        }
    }

    private void ensureMaceRecipeAvailable() {
        if (Bukkit.getRecipe(NamespacedKey.minecraft("mace")) != null) return;

        for (int i = 0; i < 3; i++) {
            if (Bukkit.getRecipe(new NamespacedKey(this, "mace-variant-" + i)) != null) return;
        }

        String[][] variants = {
                {"B  ", "S  ", "   "},
                {" B ", " S ", "   "},
                {"  B", "  S", "   "}
        };

        for (int i = 0; i < variants.length; i++) {
            NamespacedKey key = new NamespacedKey(this, "mace-variant-" + i);
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.MACE));
            recipe.shape(variants[i][0], variants[i][1], variants[i][2]);
            recipe.setIngredient('B', Material.HEAVY_CORE);
            recipe.setIngredient('S', Material.BREEZE_ROD);
            Bukkit.addRecipe(recipe);
        }

        getLogger().info("[OneMace] Mace recipe has been restored.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (isMaceOwner(player.getUniqueId())) {
            saveMaceOwner(null);
        }

        getConfig().set("offline_inventory." + player.getUniqueId(), playerHasMace(player));
        markConfigDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) return;

            getConfig().set("offline_inventory." + playerUUID, null);
            markConfigDirty();

            if (isMaceOwner(playerUUID)) {
                updateMaceNameColor(playerUUID);
            }
        }, 2L);
    }

    private boolean playerHasMace(Player player) {
        return containsMace(player.getInventory()) || containsMace(player.getEnderChest());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            if (containsMace(drop)) {
                saveMaceOwner(null);
                break;
            }
        }
    }

    @EventHandler
    public void onMaceDrop(PlayerDropItemEvent event) {
        if (!containsMace(event.getItemDrop().getItemStack())) return;

        saveMaceOwner(null);
        getConfig().set("offline_inventory." + event.getPlayer().getUniqueId(), false);
        markConfigDirty();
    }

    @EventHandler
    public void onMacePickup(EntityPickupItemEvent event) {
        if (!containsMace(event.getItem().getItemStack())) return;

        if (!maceCrafted) {
            lockMaceCrafting();
            getLogger().info("[OneMace] Mace picked up - ensuring recipes are removed.");
        }

        if (event.getEntity() instanceof Player player) {
            saveMaceOwner(player.getUniqueId());
        } else {
            saveMaceOwner(null);
        }
    }

    @EventHandler
    public void onMaceMove(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (blocksBundleInsertion(cursor, current)) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        Inventory bottom = event.getView().getBottomInventory();

        if (!isAllowedContainer(top.getType()) && isMovingMaceIntoTop(event, player, top, bottom)) {
            event.setCancelled(true);
            return;
        }

        Inventory target = getTargetInventory(event, player, top, bottom);
        if (target == null) return;

        if (target.getType() == InventoryType.PLAYER) {
            saveMaceOwner(player.getUniqueId());
        } else if (isAllowedContainer(target.getType())) {
            saveMaceOwner(null);
        }
    }

    private boolean blocksBundleInsertion(ItemStack cursor, ItemStack current) {
        return (containsMace(cursor) && MaceStorageUtil.isBundle(current))
                || (containsMace(current) && MaceStorageUtil.isBundle(cursor));
    }

    private boolean isMovingMaceIntoTop(InventoryClickEvent event, Player player, Inventory top, Inventory bottom) {
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        return switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> clickedInventory == bottom && containsMace(current);
            case NUMBER_KEY -> clickedInventory == top
                    && event.getHotbarButton() >= 0
                    && containsMace(player.getInventory().getItem(event.getHotbarButton()));
            case SWAP_OFFHAND -> clickedInventory == top && containsMace(player.getInventory().getItemInOffHand());
            case WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT -> false;
            default -> clickedInventory == top && containsMace(cursor);
        };
    }

    private Inventory getTargetInventory(InventoryClickEvent event, Player player, Inventory top, Inventory bottom) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        return switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> containsMace(current)
                    ? (event.getClickedInventory() == top ? bottom : top)
                    : null;
            case NUMBER_KEY -> {
                if (event.getHotbarButton() >= 0 && containsMace(player.getInventory().getItem(event.getHotbarButton()))) {
                    yield event.getClickedInventory();
                }
                yield containsMace(current) ? player.getInventory() : null;
            }
            case SWAP_OFFHAND -> containsMace(player.getInventory().getItemInOffHand())
                    ? event.getClickedInventory()
                    : (containsMace(current) ? player.getInventory() : null);
            default -> containsMace(cursor) ? event.getClickedInventory() : null;
        };
    }

    @EventHandler
    public void onMaceDrag(InventoryDragEvent event) {
        if (!containsMace(event.getOldCursor())) return;

        Inventory top = event.getView().getTopInventory();
        if (isAllowedContainer(top.getType())) return;

        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() != null
                && event.getRecipe().getResult().getType() == Material.MACE
                && maceCrafted) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() == null || event.getRecipe().getResult().getType() != Material.MACE) return;

        if (maceCrafted || hasKnownMace()) {
            event.setCancelled(true);
            getLogger().warning("[OneMace] Prevented duplicate Mace craft - a Mace already exists.");
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.YELLOW + "Craft the Mace with a normal click to prevent bulk crafting.");
            return;
        }

        maceCrafted = true;
        getConfig().set("settings.mace-crafted", true);
        saveMaceOwner(event.getWhoClicked().getUniqueId());
        markConfigDirty();

        ItemStack result = event.getInventory().getResult();
        if (result != null) markMace(result);

        Bukkit.getScheduler().runTask(this, this::removeAllMaceRecipes);
        getLogger().info("[OneMace] Mace crafted! Removing recipes.");

        if (getConfig().getBoolean("settings.announce-mace-messages", true)) {
            String craftedMessage = getConfig().getString("messages.crafted", "&b[OneMace] The Mace has been crafted!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', craftedMessage));
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getRecipe().getResult().getType() == Material.MACE) {
            event.setCancelled(true);
        }
    }

    public boolean hasKnownMace() {
        getLogger().info("[OneMace] Checking if a Mace exists...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerHasMace(player)) {
                getLogger().info("[OneMace] Mace found in " + player.getName() + "'s inventory or Ender Chest.");
                return true;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && containsMace(item.getItemStack())) {
                    getLogger().info("[OneMace] Mace found as a dropped item in world " + world.getName() + ".");
                    return true;
                }

                if (entity instanceof ItemFrame itemFrame && containsMace(itemFrame.getItem())) {
                    getLogger().info("[OneMace] Mace found in an item frame at " + itemFrame.getLocation() + ".");
                    return true;
                }

                if (entity instanceof InventoryHolder holder && containsMace(holder.getInventory())) {
                    getLogger().info("[OneMace] Mace found in an entity inventory in world " + world.getName() + ".");
                    return true;
                }

                if (entity instanceof LivingEntity living && living.getEquipment() != null) {
                    EntityEquipment equipment = living.getEquipment();
                    if (containsMace(equipment.getItemInMainHand()) || containsMace(equipment.getItemInOffHand())) {
                        getLogger().info("[OneMace] Mace found in an entity's equipment in world " + world.getName() + ".");
                        return true;
                    }
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder && containsMace(holder.getInventory())) {
                        getLogger().info("[OneMace] Mace found inside a block inventory at " + state.getLocation() + ".");
                        return true;
                    }
                }
            }
        }

        if (hasOfflineMaceRecord()) {
            getLogger().info("[OneMace] Mace is recorded in an offline player's inventory.");
            return true;
        }

        getLogger().info("[OneMace] No known Mace found.");
        return false;
    }

    @EventHandler
    public void onMaceBreak(PlayerItemBreakEvent event) {
        if (isMace(event.getBrokenItem())) {
            scheduleLossAudit();
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        if (containsMace(event.getEntity().getItemStack())) {
            scheduleLossAudit();
        }
    }

    @EventHandler
    public void onItemRemoved(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof Item item) || !containsMace(item.getItemStack())) return;

        RemovalReason reason = item.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;

        scheduleLossAudit();
    }

    private void scheduleLossAudit() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!hasKnownMace()) {
                resetMaceCrafting(true);
            }
        }, 50L);
    }

    @EventHandler
    public void onDirectBlockStorage(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !containsMace(event.getItem())) return;

        InventoryType type = null;
        if (block.getType() == Material.DECORATED_POT) {
            type = InventoryType.DECORATED_POT;
        } else if (MaceStorageUtil.isShelf(block.getType())) {
            type = InventoryType.SHELF;
        }

        if (type != null && !isAllowedContainer(type)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBundleUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if ((containsMace(mainHand) && MaceStorageUtil.isBundle(offHand))
                || (containsMace(offHand) && MaceStorageUtil.isBundle(mainHand))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE
                && containsMace(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (containsMace(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (containsMace(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (containsMace(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private void updateMaceNameColor(UUID ownerUUID) {
        if (!getConfig().getBoolean("settings.colored-name", false)) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team maceTeam = board.getTeam("maceHolder");
        if (maceTeam == null) {
            maceTeam = board.registerNewTeam("maceHolder");
        }

        for (String entry : Set.copyOf(maceTeam.getEntries())) {
            maceTeam.removeEntry(entry);
        }

        if (ownerUUID == null) return;

        Player player = Bukkit.getPlayer(ownerUUID);
        if (player == null) return;

        String colorName = getConfig().getString("settings.mace-name-color", "RED").toUpperCase();
        try {
            maceTeam.setColor(ChatColor.valueOf(colorName));
        } catch (IllegalArgumentException e) {
            maceTeam.setColor(ChatColor.RED);
        }
        maceTeam.addEntry(player.getName());
    }

    public void saveMaceOwner(UUID ownerUUID) {
        getConfig().set("settings.mace-owner", ownerUUID == null ? null : ownerUUID.toString());
        markConfigDirty();
        updateMaceNameColor(ownerUUID);
    }

    public UUID getMaceOwner() {
        String ownerUUID = getConfig().getString("settings.mace-owner");
        if (ownerUUID == null) return null;

        try {
            return UUID.fromString(ownerUUID);
        } catch (IllegalArgumentException e) {
            getLogger().warning("[OneMace] Invalid mace-owner UUID in config; clearing it.");
            getConfig().set("settings.mace-owner", null);
            markConfigDirty();
            return null;
        }
    }

    public boolean isMaceOwner(UUID playerUUID) {
        UUID maceOwner = getMaceOwner();
        return maceOwner != null && maceOwner.equals(playerUUID);
    }
}
