package eu.minelife.mLVoidChest.managers;

import eu.minelife.mLVoidChest.MLVoidChest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages player preferences for the MLVoidChest plugin
 */
public class PlayerPreferenceManager {
    private final MLVoidChest plugin;
    private final Map<UUID, Boolean> messageToggles;
    private final File preferencesFolder;

    public PlayerPreferenceManager(MLVoidChest plugin) {
        this.plugin = plugin;
        this.messageToggles = new HashMap<>();
        
        // Create preferences folder
        this.preferencesFolder = new File(plugin.getDataFolder(), "preferences");
        if (!preferencesFolder.exists()) {
            preferencesFolder.mkdirs();
        }
    }

    /**
     * Checks if a player has sale messages enabled
     * 
     * @param playerUUID The UUID of the player
     * @return true if messages are enabled, false if disabled
     */
    public boolean hasMessagesEnabled(UUID playerUUID) {
        // If we don't have a stored preference, use the default (true)
        return messageToggles.getOrDefault(playerUUID, true);
    }

    /**
     * Toggles the sale messages for a player
     * 
     * @param playerUUID The UUID of the player
     * @return The new state (true if enabled, false if disabled)
     */
    public boolean toggleMessages(UUID playerUUID) {
        boolean currentState = hasMessagesEnabled(playerUUID);
        boolean newState = !currentState;
        
        messageToggles.put(playerUUID, newState);
        savePlayerPreferences(playerUUID);
        
        return newState;
    }

    /**
     * Saves a player's preferences to file
     * 
     * @param playerUUID The UUID of the player
     */
    public void savePlayerPreferences(UUID playerUUID) {
        File playerFile = new File(preferencesFolder, playerUUID.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        // Only save if we have a preference stored
        if (messageToggles.containsKey(playerUUID)) {
            config.set("messages-enabled", messageToggles.get(playerUUID));
            
            try {
                config.save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player preferences for " + playerUUID, e);
            }
        }
    }

    /**
     * Loads a player's preferences from file
     * 
     * @param playerUUID The UUID of the player
     */
    public void loadPlayerPreferences(UUID playerUUID) {
        File playerFile = new File(preferencesFolder, playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        if (config.contains("messages-enabled")) {
            messageToggles.put(playerUUID, config.getBoolean("messages-enabled"));
        }
    }

    /**
     * Loads all player preferences
     */
    public void loadAllPlayerPreferences() {
        File[] playerFiles = preferencesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null) {
            return;
        }

        for (File file : playerFiles) {
            String fileName = file.getName();
            if (fileName.endsWith(".yml")) {
                try {
                    UUID playerUUID = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                    loadPlayerPreferences(playerUUID);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid player preference file name: " + fileName);
                }
            }
        }
    }

    /**
     * Saves all player preferences
     */
    public void saveAllPlayerPreferences() {
        for (UUID playerUUID : messageToggles.keySet()) {
            savePlayerPreferences(playerUUID);
        }
    }
}