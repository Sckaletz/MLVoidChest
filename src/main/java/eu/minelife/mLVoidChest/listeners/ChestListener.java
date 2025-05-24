package eu.minelife.mLVoidChest.listeners;

import eu.minelife.mLVoidChest.MLVoidChest;
import eu.minelife.mLVoidChest.utils.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ChestListener implements Listener {
    private final MLVoidChest plugin;

    public ChestListener(MLVoidChest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChestPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();

        // Check if the block is a chest
        if (block.getType() != Material.CHEST) {
            return;
        }

        // Check if the item has a display name
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        // Check if it's a void chest
        String chestName = plugin.getConfig().getString("chest.name", "&b&lML Void Chest");
        String displayName = meta.getDisplayName();

        if (!ChatColor.stripColor(displayName).equals(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', chestName)))) {
            return;
        }

        // Check if player has permission to use void chests
        if (!player.hasPermission("mlvoidchest.use")) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.no-permission")));
            return;
        }

        // Check if the chunk already has a chest
        if (plugin.getChestManager().hasChestInChunk(block.getChunk())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chunk-has-chest")));
            return;
        }

        // Check if player has reached their chest limit
        if (!plugin.getChestManager().addChest(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-limit-reached")));
            return;
        }

        // Notify player
        player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-placed")));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChestBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if the block is a chest
        if (block.getType() != Material.CHEST) {
            return;
        }

        // Check if it's a void chest
        if (plugin.getChestManager().getChestOwner(block.getLocation()) == null) {
            return;
        }

        // Remove the chest from the manager
        plugin.getChestManager().removeChest(block.getLocation());

        // Notify player
        player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-broken")));
    }
}
