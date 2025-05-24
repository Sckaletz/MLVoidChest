package eu.minelife.mLVoidChest.commands;

import eu.minelife.mLVoidChest.MLVoidChest;
import eu.minelife.mLVoidChest.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VoidChestCommand implements CommandExecutor, TabCompleter {
    private final MLVoidChest plugin;

    public VoidChestCommand(MLVoidChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for toggle subcommand
        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            // Only players can toggle their messages
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can toggle their sale messages.");
                return true;
            }

            Player player = (Player) sender;

            // Check permission
            if (!player.hasPermission("mlvoidchest.toggle")) {
                sender.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }

            // Toggle the player's message preference
            boolean newState = plugin.getPreferenceManager().toggleMessages(player.getUniqueId());

            // Send confirmation message
            if (newState) {
                player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.messages-enabled", "&aSale messages have been &eenabled&a.")));
            } else {
                player.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.messages-disabled", "&aSale messages have been &cdisabled&a.")));
            }

            return true;
        }

        // Handle the give command
        if (!sender.hasPermission("mlvoidchest.give")) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        Player target;

        if (args.length > 0) {
            // Give to specified player
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player) {
            // Give to command sender
            target = (Player) sender;
        } else {
            // Console must specify a player
            sender.sendMessage(ChatColor.RED + "Usage: /voidchest <player> or /voidchest toggle");
            return true;
        }

        // Create the void chest item
        ItemStack chest = createVoidChest();

        // Give the chest to the player
        if (target.getInventory().firstEmpty() == -1) {
            // Inventory is full, drop at player's location
            target.getWorld().dropItem(target.getLocation(), chest);
            target.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-given") + " " + 
                    ChatColor.YELLOW + "Your inventory was full, so it was dropped at your feet."));
        } else {
            // Add to inventory
            target.getInventory().addItem(chest);
            target.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-given")));
        }

        // Notify the command sender if they're not the target
        if (sender != target) {
            sender.sendMessage(MessageUtils.formatMessage(plugin.getConfig().getString("messages.chest-given-other")
                    .replace("%player%", target.getName())));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument can be "toggle" or a player name
            if ("toggle".startsWith(args[0].toLowerCase())) {
                completions.add("toggle");
            }

            // Add online player names that match
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }

    /**
     * Creates a void chest item with custom name and lore
     * 
     * @return The void chest item
     */
    private ItemStack createVoidChest() {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();

        if (meta != null) {
            // Set custom name
            String name = plugin.getConfig().getString("chest.name", "&b&lML Void Chest");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            // Set lore
            List<String> configLore = plugin.getConfig().getStringList("chest.lore");
            List<String> lore = new ArrayList<>();

            for (String line : configLore) {
                line = line.replace("%interval%", String.valueOf(plugin.getConfig().getInt("collection-interval", 15)));
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            meta.setLore(lore);
            chest.setItemMeta(meta);
        }

        return chest;
    }
}
