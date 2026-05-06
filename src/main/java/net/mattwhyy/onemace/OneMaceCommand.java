package net.mattwhyy.onemace;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.StringUtil;

import java.util.*;

public class OneMaceCommand implements CommandExecutor, TabCompleter {
    private final OneMace plugin;
    private final List<String> subCommands = Arrays.asList("locate", "info", "fix");

    public OneMaceCommand(OneMace plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /onemace <locate> | <info> | <fix>");
            return true;
        }


        if (args[0].equalsIgnoreCase("locate")) {
            boolean allowAll = plugin.getConfig().getBoolean("settings.allow-locate-for-all", false);

            if (!allowAll && !sender.hasPermission("onemace.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Locating the Mace...");
            Bukkit.getScheduler().runTask(plugin, () -> locateMace(sender));
            return true;
        }

        if (args[0].equalsIgnoreCase("fix")) {
            if (!sender.hasPermission("onemace.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            Bukkit.getScheduler().runTask(plugin, () -> fixDuplicateMaces(sender));
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(ChatColor.YELLOW + "Ensuring only one Mace exists on the server.");
            sender.sendMessage(ChatColor.GRAY + "If the Mace is destroyed, crafting is restored.");
            sender.sendMessage(ChatColor.GRAY + "Use /onemace locate to manually verify Mace status.");
            sender.sendMessage(ChatColor.GRAY + "Use /onemace fix to remove duplicate Maces.");
            sender.sendMessage(ChatColor.GRAY + "If you need support, feel free to message me on Discord.");
            sender.sendMessage(ChatColor.GOLD + "made by mattwhyy <3");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid subcommand. Use /onemace <locate> | <info> | <fix>");
        return true;
    }

    private void sendTeleportMessage(CommandSender sender, String message, Location loc) {
        String coords = ChatColor.GOLD + "X: " + loc.getBlockX()
                + " Y: " + loc.getBlockY()
                + " Z: " + loc.getBlockZ()
                + ChatColor.GRAY + " in world " + loc.getWorld().getName();

        if (sender instanceof Player player && player.isOp()) {

            String command = "/tp " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();

            TextComponent text = new TextComponent(TextComponent.fromLegacyText(message + " " + coords + " "));

            TextComponent click = new TextComponent("[TELEPORT]");
            click.setColor(ChatColor.GREEN.asBungee());
            click.setBold(true);

            click.setClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND,
                    command
            ));
            click.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Click to teleport").create()
            ));

            player.spigot().sendMessage(text, click);
        } else {
            sender.sendMessage(message + " " + coords);
        }
    }

    private void sendPlayerTeleport(CommandSender sender, Player target, String where) {
        if (!(sender instanceof Player p)) return;

        if (p.isOp()) {
            String cmd = "/tp " + target.getName();

            TextComponent msg = new TextComponent(ChatColor.GREEN + "The Mace is in "
                    + ChatColor.AQUA + target.getName() + "'s " + where + " ");

            TextComponent click = new TextComponent("[TELEPORT]");
            click.setColor(ChatColor.GREEN.asBungee());
            click.setBold(true);
            click.setClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND,
                    cmd
            ));
            click.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Click to teleport").create()
            ));

            p.spigot().sendMessage(msg, click);
        } else {
            p.sendMessage(ChatColor.GREEN + "The Mace is in " + ChatColor.AQUA + target.getName() + "'s " + where + ".");
        }
    }

    private void fixDuplicateMaces(CommandSender sender) {
        List<ItemStack> foundMaces = new ArrayList<>();
        List<ItemStack> duplicates = new ArrayList<>();

        sender.sendMessage(ChatColor.YELLOW + "Running Mace scan...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isAnyMace(item)) foundMaces.add(item);
                else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isAnyMace(item)) foundMaces.add(item);
                else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractHorse horse) {
                    for (ItemStack item : horse.getInventory().getContents()) {
                        if (isAnyMace(item)) foundMaces.add(item);
                        else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                        else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    ItemStack droppedItem = item.getItemStack();
                    if (isAnyMace(droppedItem)) foundMaces.add(droppedItem);
                    else if (isMaceInsideShulker(droppedItem)) foundMaces.addAll(getMacesFromShulker(droppedItem));
                    else if (isMaceInsideBundle(droppedItem)) foundMaces.addAll(getMacesFromBundle(droppedItem));
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        for (ItemStack item : container.getInventory().getContents()) {
                            if (isAnyMace(item)) foundMaces.add(item);
                            else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                            else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
                        }
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof StorageMinecart minecart) {
                    for (ItemStack item : minecart.getInventory().getContents()) {
                        if (isAnyMace(item)) foundMaces.add(item);
                        else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                        else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
                    }
                }
                if (entity instanceof ChestBoat chestBoat) {
                    for (ItemStack item : chestBoat.getInventory().getContents()) {
                        if (isAnyMace(item)) foundMaces.add(item);
                        else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
                        else if (isMaceInsideBundle(item)) foundMaces.addAll(getMacesFromBundle(item));
                    }
                }
            }
        }

        if (foundMaces.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No Mace found. Enabling crafting.");
            plugin.resetMaceCrafting(true);
            return;
        }

        ItemStack officialMace = foundMaces.get(0);
        plugin.markMace(officialMace);
        sender.sendMessage(ChatColor.GREEN + "Removed all duplicate Maces (if they existed).");

        for (int i = 1; i < foundMaces.size(); i++) {
            duplicates.add(foundMaces.get(i));
        }

        for (ItemStack duplicate : duplicates) {
            duplicate.setAmount(0);
        }

        plugin.getConfig().set("settings.mace-crafted", true);
        plugin.saveConfig();

        Bukkit.getScheduler().runTask(plugin, plugin::removeAllMaceRecipes);
        sender.sendMessage(ChatColor.RED + "Recipe removed to prevent further crafting.");
    }


    private boolean isAnyMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    private void locateMace(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item))  {
                    sendPlayerTeleport(sender, player, "Inventory");
                    return;
                }
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item)) {
                    sendPlayerTeleport(sender, player, "Ender Chest");
                    return;
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractHorse horse) {
                    for (ItemStack item : horse.getInventory().getContents()) {
                        if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item)) {
                            sendTeleportMessage(sender,
                                    ChatColor.YELLOW + "The Mace is in a storage animal at",
                                    entity.getLocation());
                            return;
                        }
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    ItemStack droppedItem = item.getItemStack();
                    if (isAnyMace(droppedItem) || isMaceInsideShulker(droppedItem) || isMaceInsideBundle(droppedItem)) {
                        Location loc = entity.getLocation();
                        sendTeleportMessage(sender,
                                ChatColor.YELLOW + "The Mace is dropped at",
                                loc);
                        return;
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        Inventory inv = container.getInventory();
                        for (ItemStack item : inv.getContents()) {
                            if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item)) {
                                Location loc = state.getLocation();
                                sendTeleportMessage(sender,
                                        ChatColor.YELLOW + "The Mace is stored in a container at",
                                        loc);
                                return;
                            }
                        }
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof StorageMinecart minecart) {
                    Inventory inv = minecart.getInventory();
                    for (ItemStack item : inv.getContents()) {
                        if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item)) {
                            sendTeleportMessage(sender,
                                    ChatColor.YELLOW + "The Mace is in a storage minecart at",
                                    entity.getLocation());
                            return;
                        }
                    }
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ChestBoat chestBoat) {
                    Inventory inv = chestBoat.getInventory();
                    for (ItemStack item : inv.getContents()) {
                        if (isAnyMace(item) || isMaceInsideShulker(item) || isMaceInsideBundle(item)) {
                            sendTeleportMessage(sender,
                                    ChatColor.YELLOW + "The Mace is in a Chest Boat at",
                                    entity.getLocation());
                            return;
                        }
                    }
                }
            }
        }

        if (plugin.getConfig().isConfigurationSection("offline_inventory")) {
            for (String uuid : plugin.getConfig().getConfigurationSection("offline_inventory").getKeys(true)) {
                if (plugin.getConfig().getBoolean("offline_inventory." + uuid, false)) {
                    UUID offlineUUID = UUID.fromString(uuid);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(offlineUUID);
                    String name = offlinePlayer.getName();

                    if (name != null) {
                        sender.sendMessage(ChatColor.YELLOW + "The Mace is in " + ChatColor.AQUA + name + ChatColor.YELLOW + "'s inventory (offline).");
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "The Mace is in an offline player's inventory (UUID: " + uuid + ").");
                    }
                    return;
                }
            }
        }

        sender.sendMessage(ChatColor.RED + "The Mace is either missing or in an unloaded chunk.");
    }


    private boolean isMaceInsideShulker(ItemStack item) {
        if (item == null || item.getType() != Material.SHULKER_BOX) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta) {
            if (blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
                Inventory shulkerInv = shulkerBox.getInventory();
                for (ItemStack storedItem : shulkerInv.getContents()) {
                    if (plugin.isMace(storedItem)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private List<ItemStack> getMacesFromShulker(ItemStack item) {
        List<ItemStack> maces = new ArrayList<>();
        if (item == null || item.getType() != Material.SHULKER_BOX) {
            return maces;
        }

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta == null || !(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return maces;
        }

        Inventory shulkerInv = shulkerBox.getInventory();
        for (ItemStack storedItem : shulkerInv.getContents()) {
            if (isAnyMace(storedItem)) {
                maces.add(storedItem);
            }
        }
        return maces;
    }

    private boolean isMaceInsideBundle(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BundleMeta bundleMeta)) {
            return false;
        }
        for (ItemStack stored : bundleMeta.getItems()) {
            if (isAnyMace(stored) || isMaceInsideShulker(stored) || isMaceInsideBundle(stored)) {
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> getMacesFromBundle(ItemStack item) {
        List<ItemStack> maces = new ArrayList<>();
        if (item == null || !(item.getItemMeta() instanceof BundleMeta bundleMeta)) {
            return maces;
        }
        for (ItemStack stored : bundleMeta.getItems()) {
            if (isAnyMace(stored)) {
                maces.add(stored);
            } else if (isMaceInsideShulker(stored)) {
                maces.addAll(getMacesFromShulker(stored));
            } else if (isMaceInsideBundle(stored)) {
                maces.addAll(getMacesFromBundle(stored));
            }
        }
        return maces;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            completions.sort(String::compareToIgnoreCase);
            return completions;
        }
        return new ArrayList<>();
    }
}
