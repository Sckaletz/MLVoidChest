package eu.minelife.mLVoidChest.listeners;

import eu.minelife.mLVoidChest.MLVoidChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player-related events for the MLVoidChest plugin
 */
public class PlayerListener implements Listener {
    private final MLVoidChest plugin;

    public PlayerListener(MLVoidChest plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player join events
     * Reloads the player's chest data and preferences when they join
     * 
     * @param event The player join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Reload the player's chest data
        plugin.getChestManager().loadPlayerData(player.getUniqueId());
        
        // Reload the player's preferences
        plugin.getPreferenceManager().loadPlayerPreferences(player.getUniqueId());
        
        // Log that the player's data was reloaded
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Reloaded chest data and preferences for player: " + player.getName());
        }
    }
}