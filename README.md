# MLVoidChest

**MLVoidChest** is a Minecraft plugin for version **1.21.4** that adds an **automatic selling system** using a special Voidchest.

## 📦 Features

- Automatically sells **all items on the ground** in the **same chunk** as the Voidchest every **15 seconds**.
- Also sells **items inside the Voidchest inventory**.
- Only sells items that are **configured for sale** in **ShopGUIPlus**.
- Displays a **hologram** above the Voidchest using **DecentHolograms**.
- Works seamlessly with **item stacking** using **WildStacker**.

## 🛠 Requirements

This plugin depends on the following plugins to function properly:

- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- [ShopGUIPlus](https://www.spigotmc.org/resources/shopgui-plus.6515/) *(premium)*
- [WildStacker](https://www.spigotmc.org/resources/wildstacker.32457/](https://bg-software.com/wildstacker/)
- [DecentHolograms](https://www.spigotmc.org/resources/decent-holograms-1-8-1-20-4.96927/)

## 💡 How It Works

1. **Place a Voidchest** on the ground.
2. Every **15 seconds**, the plugin:
   - Checks for **items on the ground in the same chunk** as the Voidchest and sells them if they match entries in ShopGUIPlus.
   - **Sells items stored inside the chest**.
3. Holograms provide visual feedback on the chest's function.

## 📥 Installation

1. Download the latest version of MLVoidChest.
2. Place it in your server's `/plugins` folder.
3. Make sure you have all the required dependencies installed and configured.
4. Restart your server.
