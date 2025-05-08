[Modrinth](https://modrinth.com/plugin/onemace) | [CurseForge](https://curseforge.com/minecraft/bukkit-plugins/onemace) | [Discord](https://discordapp.com/users/555629040455909406) | [GitHub](https://github.com/mattwhyy/OneMace)
# OneMace
**OneMace** introduces a unique gameplay twist by limiting the **entire server** to a **single Mace**. No more spamming Maces—strategy and skill are key! Perfect for **Lifesteal** and **competitive** **PvP** servers that want to balance the game.

- The plugin stores the crafted state in **config.yml**, so it remains saved across **restarts**.
- The Mace recipe is **re-enabled** if the Mace is **destroyed** (e.g., lava, void, breaking).
- Announcements for crafting & destruction can be enabled/disabled in **config.yml**.

## Configuration
```
settings:
  mace-crafted: false  # Changes to true when the Mace is crafted
  mace-owner: null  # Stores the UUID of the current owner, or null if no owner
  announce-mace-messages: true  # If true, broadcasts when the Mace is crafted or lost

# Stores whether an offline player logged out with the Mace.
# If set to true, the Mace is in their inventory or Ender Chest when they logged out.
# Used to prevent crafting if the Mace still exists in an offline player’s possession.
# This is automatically updated when a player logs out or the server shuts down.
offline_inventory: {}

# Customizable messages
messages:
  crafted: "&b[OneMace] &eThe Mace has been crafted!"
  lost: "&b[OneMace] &eThe Mace has been lost!"
```
## Commands
```/onemace locate``` -
Allows you to locate the Mace.

**Required permission:** 
```onemace.admin```
- description: Allows using admin subcommands
- default: op

```/onemace fix``` -
Allows you to remove duplicate Maces and only leave one.

**Required permission:** 
```onemace.admin```
- description: Allows using admin subcommands
- default: op

```/onemace info``` -
Basic information about the plugin.

**Required permission:** 
none

## Setup
Put the ```jar``` file into your server's **plugin folder**.

**Restart** (or reload) the server!

## Bypasses & Limitations
⚠️ **Maces in unloaded chunks will NOT be detected. This is unfortunately unfixable. It is up to you to detect and use the admin commands if you suspect there may be more than one Mace.**

Otherwise, this plugin should work perfectly on a clean vanilla server.

If you find any **vanilla bypass**, please contact me so I can fix it.


## Special Thanks
💙 **Kamco0990** – The reason this plugin even exists.
