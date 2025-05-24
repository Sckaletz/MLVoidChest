package eu.minelife.mLVoidChest.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import eu.minelife.mLVoidChest.MLVoidChest;

/**
 * Utility class for handling message formatting
 */
public class MessageUtils {
    
    /**
     * Formats a message with color codes and the plugin prefix
     * 
     * @param message The message to format
     * @return The formatted message
     */
    public static String formatMessage(String message) {
        if (message == null) {
            return "";
        }
        
        FileConfiguration config = MLVoidChest.getInstance().getConfig();
        String prefix = config.getString("messages.prefix", "&8[&bMLVoidChest&8] &7");
        
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
    
    /**
     * Formats a message with color codes without adding the prefix
     * 
     * @param message The message to format
     * @return The formatted message
     */
    public static String formatMessageNoPrefix(String message) {
        if (message == null) {
            return "";
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}