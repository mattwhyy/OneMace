# OneMace

[![Download on Modrinth](https://img.shields.io/modrinth/dt/onemace?style=plastic&logo=modrinth&label=Download%20on%20Modrinth&link=https%3A%2F%2Fmodrinth.com%2Fmod%2Fmusichud)](https://modrinth.com/plugin/onemace)
[![Download on GitHub](https://img.shields.io/github/downloads/mattwhyy/OneMace/total?style=plastic&logo=github&label=Download%20on%20GitHub)](https://github.com/mattwhyy/OneMace/releases)

**Tired of plugins that _claim_ to fix multiple Maces—but don’t?**

OneMace enforces a hard limit: players can have only **one** Mace. **No** bypasses. **Period.**

**OneMace** introduces a unique gameplay twist by limiting the **entire server** to a **single Mace**. No more spamming Maces—strategy and skill are key! Perfect for **Lifesteal** and **competitive** **PvP** servers that want to balance the game.

- The plugin stores the crafted state in **config.yml**, so it remains saved across **restarts**.
- The Mace recipe is **re-enabled** if the Mace is **destroyed** (e.g., lava, void, breaking).
- Announcements for crafting & destruction can be enabled/disabled in **config.yml**.
- Maces are tracked inside supported nested item storage and modern block/entity inventories.

##
Need a **server** for a nice deal or just want to **support me**? ❤️

[![ScalaCube 50 PERCENT OFF FOR THE FIRST MONTH](https://img.shields.io/badge/ScalaCube-50%25%20Off%201st%20Month-orange?style=plastic
)](https://scalacube.com/p/_hosting_server_minecraft/4966506)
[![Support me on Ko-Fi](https://img.shields.io/badge/Ko%E2%8E%AFFi-Support%20Me-blue?style=plastic&logo=kofi&logoColor=pink)](https://ko-fi.com/mattwhyy)

## Configuration
_Make sure the server is not running before making changes!_
```
settings:
  mace-crafted: false  # Changes to true when the Mace is crafted.
  mace-owner: null  # Stores the UUID of the current owner, or null if no owner.
  announce-mace-messages: true  # If true, broadcasts when the Mace is crafted or lost.
  allow-locate-for-all: false # If true, allows any player to use the '/onemace locate' subcommand.
  colored-name: false # If true, the player that has the Mace will have a colored name.
  mace-name-color: "RED" # The color of the name, use Chat Colors.
  optional-allowed-containers: # Containers that the Mace is allowed to be placed into.
    - ENDER_CHEST
    - ANVIL
    - ENCHANTING

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
Put the ```jar``` file into your server's **plugins** folder.

**Restart** the server.

## Bypasses & Limitations
⚠️ **Maces in unloaded chunks cannot be scanned directly.** OneMace keeps crafting disabled when the persisted state says a Mace still exists, including tracked offline-player inventories. `/onemace fix` will not assume the Mace is gone just because it cannot currently be found in loaded chunks.

Otherwise, this plugin should work perfectly on a clean vanilla server.
## Contact

If you find any **vanilla bypass** or just need **help**, feel free to contact me:

[![Discord Contact](https://img.shields.io/badge/Contact%20on-Discord-%235865f2?logo=discord&style=plastic&link=https%3A%2F%2Fdiscordapp.com%2Fusers%2F555629040455909406)](https://discord.com/users/555629040455909406)
[![GitHub](https://img.shields.io/badge/Contact%20on-GitHub-green?style=plastic&logo=github&link=left-https%3A%2F%2Fgithub.com%2Fmattwhyy%2FOneMace%2Fissues
)](https://github.com/mattwhyy/OneMace/issues)

## Special Thanks
💙 **Kamco0990** – The reason this plugin even exists.
