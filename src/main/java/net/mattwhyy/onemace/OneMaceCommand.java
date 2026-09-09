package net.mattwhyy.onemace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

    private void sendTeleportMessage(CommandSender sender, String message, Location location) {
        Component base = Component.text(message + " ", NamedTextColor.YELLOW)
                .append(Component.text(
                        "X: " + location.getBlockX()
                                + " Y: " + location.getBlockY()
                                + " Z: " + location.getBlockZ(),
                        NamedTextColor.GOLD
                ))
                .append(Component.text(" in world " + location.getWorld().getName(), NamedTextColor.GRAY));

        if (sender instanceof Player player && player.isOp()) {
            String command = "/tp " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
            Component teleport = Component.text(" [TELEPORT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.suggestCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));
            player.sendMessage(base.append(teleport));
        } else {
            sender.sendMessage(base);
        }
    }

    private void sendPlayerTeleport(CommandSender sender, Player target, String where) {
        Component base = Component.text("The Mace is in ", NamedTextColor.GREEN)
                .append(Component.text(target.getName() + "'s " + where, NamedTextColor.AQUA));

        if (sender instanceof Player player && player.isOp()) {
            Component teleport = Component.text(" [TELEPORT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.suggestCommand("/tp " + target.getName()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));
            player.sendMessage(base.append(teleport));
        } else {
            sender.sendMessage(base);
        }
    }

    private void fixDuplicateMaces(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Running Mace scan...");

        MaceStorageUtil.CleanupState cleanup = new MaceStorageUtil.CleanupState();

        for (Player player : Bukkit.getOnlinePlayers()) {
            MaceStorageUtil.removeExtraMaces(player.getInventory(), cleanup);
            MaceStorageUtil.removeExtraMaces(player.getEnderChest(), cleanup);
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;

                if (entity instanceof Item item) {
                    ItemStack cleaned = MaceStorageUtil.removeExtraMaces(item.getItemStack(), cleanup);
                    if (cleaned == null || cleaned.getType().isAir()) {
                        item.remove();
                    } else {
                        item.setItemStack(cleaned);
                    }
                    continue;
                }

                if (entity instanceof InventoryHolder holder) {
                    MaceStorageUtil.removeExtraMaces(holder.getInventory(), cleanup);
                }

                if (entity instanceof LivingEntity living && !(entity instanceof InventoryHolder)) {
                    cleanEquipment(living.getEquipment(), cleanup);
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder) {
                        MaceStorageUtil.removeExtraMaces(holder.getInventory(), cleanup);
                    }
                }
            }
        }

        if (!cleanup.keptOne()) {
            if (plugin.hasOfflineMaceRecord()) {
                plugin.lockMaceCrafting();
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "A Mace is recorded in an offline player's inventory. Crafting remains disabled.");
                sender.sendMessage(ChatColor.GRAY + "/onemace fix cannot inspect an offline player's inventory until they join.");
                return;
            }

            sender.sendMessage(ChatColor.GREEN + "No Mace found. Enabling crafting.");
            plugin.resetMaceCrafting(true);
            plugin.saveConfig();
            return;
        }

        plugin.lockMaceCrafting();
        plugin.saveConfig();

        if (cleanup.removed() > 0) {
            sender.sendMessage(ChatColor.GREEN + "Removed " + cleanup.removed() + " duplicate Mace"
                    + (cleanup.removed() == 1 ? "." : "s."));
        } else {
            sender.sendMessage(ChatColor.GREEN + "One Mace found. No duplicates needed removing.");
        }

        if (plugin.hasOfflineMaceRecord()) {
            sender.sendMessage(ChatColor.YELLOW + "An offline-player Mace record also exists, so an offline duplicate may still remain.");
        }

        sender.sendMessage(ChatColor.RED + "Recipe removed to prevent further crafting.");
    }

    private void cleanEquipment(EntityEquipment equipment, MaceStorageUtil.CleanupState cleanup) {
        if (equipment == null) return;

        ItemStack mainHand = MaceStorageUtil.removeExtraMaces(equipment.getItemInMainHand(), cleanup);
        ItemStack offHand = MaceStorageUtil.removeExtraMaces(equipment.getItemInOffHand(), cleanup);
        equipment.setItemInMainHand(mainHand == null ? new ItemStack(Material.AIR) : mainHand);
        equipment.setItemInOffHand(offHand == null ? new ItemStack(Material.AIR) : offHand);
    }

    private void locateMace(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.containsMace(player.getInventory())) {
                sendPlayerTeleport(sender, player, "Inventory");
                return;
            }
            if (plugin.containsMace(player.getEnderChest())) {
                sendPlayerTeleport(sender, player, "Ender Chest");
                return;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;

                if (entity instanceof Item item && plugin.containsMace(item.getItemStack())) {
                    sendTeleportMessage(sender, "The Mace is dropped at", entity.getLocation());
                    return;
                }

                if (entity instanceof InventoryHolder holder && plugin.containsMace(holder.getInventory())) {
                    sendTeleportMessage(sender, "The Mace is in an entity inventory at", entity.getLocation());
                    return;
                }

                if (entity instanceof LivingEntity living && equipmentContainsMace(living.getEquipment())) {
                    sendTeleportMessage(sender, "The Mace is held by an entity at", entity.getLocation());
                    return;
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder && plugin.containsMace(holder.getInventory())) {
                        sendTeleportMessage(sender, "The Mace is stored in a block inventory at", state.getLocation());
                        return;
                    }
                }
            }
        }

        if (plugin.getConfig().isConfigurationSection("offline_inventory")) {
            for (String uuid : plugin.getConfig().getConfigurationSection("offline_inventory").getKeys(false)) {
                if (!plugin.getConfig().getBoolean("offline_inventory." + uuid, false)) continue;

                try {
                    UUID offlineUUID = UUID.fromString(uuid);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(offlineUUID);
                    String name = offlinePlayer.getName();
                    if (name != null) {
                        sender.sendMessage(ChatColor.YELLOW + "The Mace is in " + ChatColor.AQUA + name
                                + ChatColor.YELLOW + "'s inventory (offline).");
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "The Mace is in an offline player's inventory (UUID: " + uuid + ").");
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.YELLOW + "The Mace is recorded in an offline player's inventory.");
                }
                return;
            }
        }

        sender.sendMessage(ChatColor.RED + "The Mace is either missing or in an unloaded chunk.");
    }

    private boolean equipmentContainsMace(EntityEquipment equipment) {
        return equipment != null
                && (plugin.containsMace(equipment.getItemInMainHand())
                || plugin.containsMace(equipment.getItemInOffHand()));
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
