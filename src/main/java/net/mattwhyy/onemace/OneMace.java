package net.mattwhyy.onemace;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;

public class OneMace extends JavaPlugin implements Listener {
    private boolean maceCrafted = false;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Load the config.yml file
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("[OneMace] Plugin enabled!");
    }


    @Override
    public void onDisable() {
        getLogger().info("[OneMace] Plugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("onemace-reload")) {
            reloadConfig(); // Reloads the config while keeping user changes
            sender.sendMessage("§8[OneMace]§r Config reloaded!");
            return true;
        }
        return false;
    }


    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (maceCrafted) return;

        if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.MACE) {
            maceCrafted = true;
            Bukkit.getScheduler().runTask(this, this::removeAllMaceRecipes);
            getLogger().info("[OneMace] Mace crafted! Removing all recipes.");

            // Check if broadcasting is enabled in config
            if (getConfig().getBoolean("settings.announce-mace-craft", true)) {
                Bukkit.broadcastMessage("§8[OneMace]§r §5§lThe Mace has been crafted!");
            }
        }
    }

    private void removeAllMaceRecipes() {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe.getResult().getType() == Material.MACE) {
                iterator.remove();
                getLogger().info("[OneMace] Mace recipe removed.");
            }
        }
    }
}
