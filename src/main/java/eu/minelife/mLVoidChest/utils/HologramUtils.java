package eu.minelife.mLVoidChest.utils;

import eu.minelife.mLVoidChest.MLVoidChest;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for managing holograms above void chests
 */
public class HologramUtils {
    private static final Map<Location, String> hologramIds = new HashMap<>();
    private static final Map<Location, Long> lastCollectionTimes = new HashMap<>();
    private static final String HOLOGRAM_PREFIX = "voidchest_";

    /**
     * Creates a hologram above a void chest
     *
     * @param location The location of the chest
     * @param ownerUUID The UUID of the chest owner
     * @return true if the hologram was created, false otherwise
     */
    public static boolean createHologram(Location location, UUID ownerUUID) {
        try {
            // Get the plugin instance
            MLVoidChest plugin = MLVoidChest.getInstance();
            FileConfiguration config = plugin.getConfig();

            // Check if holograms are enabled
            if (!config.getBoolean("hologram.enabled", true)) {
                return false;
            }

            // Get the hologram text from config
            String hologramText = MessageUtils.formatMessageNoPrefix(config.getString("hologram.text", "&b&lML Void Chest"));

            // Create a list of lines for the hologram
            List<String> lines = new ArrayList<>();
            lines.add(hologramText);

            // Add countdown line if enabled
            if (config.getBoolean("hologram.countdown.enabled", true)) {
                String countdownFormat = config.getString("hologram.countdown.format", "&7Next collection: &e%time%");
                countdownFormat = countdownFormat.replace("%time%", "Calculating...");
                lines.add(MessageUtils.formatMessageNoPrefix(countdownFormat));
            }

            // Create a unique ID for this hologram
            String hologramId = HOLOGRAM_PREFIX + location.getWorld().getName() + "_" + 
                    location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();

            // Create a new location 1 block above the chest
            Location hologramLocation = location.clone().add(0.5, 1.5, 0.5);

            // Create the hologram
            Hologram hologram = DHAPI.createHologram(hologramId, hologramLocation, lines);

            // Store the hologram ID
            hologramIds.put(location, hologramId);

            // Initialize the last collection time
            lastCollectionTimes.put(location, System.currentTimeMillis());

            // Update the countdown immediately
            updateHologramCountdown(location);

            return hologram != null;
        } catch (Exception e) {
            MLVoidChest.getInstance().getLogger().warning("Failed to create hologram: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a hologram above a void chest
     *
     * @param location The location of the chest
     */
    public static void removeHologram(Location location) {
        try {
            // Get the hologram ID
            String hologramId = hologramIds.remove(location);
            if (hologramId == null) {
                // Try to generate the ID if it's not in the map
                hologramId = HOLOGRAM_PREFIX + location.getWorld().getName() + "_" + 
                        location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
            }

            // Delete the hologram
            DHAPI.removeHologram(hologramId);
        } catch (Exception e) {
            MLVoidChest.getInstance().getLogger().warning("Failed to remove hologram: " + e.getMessage());
        }
    }

    /**
     * Removes all holograms
     */
    public static void removeAllHolograms() {
        for (String hologramId : hologramIds.values()) {
            try {
                DHAPI.removeHologram(hologramId);
            } catch (Exception e) {
                MLVoidChest.getInstance().getLogger().warning("Failed to remove hologram: " + e.getMessage());
            }
        }
        hologramIds.clear();
        lastCollectionTimes.clear();
    }

    /**
     * Updates the hologram countdown for a specific chest
     *
     * @param location The location of the chest
     */
    public static void updateHologramCountdown(Location location) {
        try {
            // Get the hologram ID
            String hologramId = hologramIds.get(location);
            if (hologramId == null) {
                // Try to generate the ID if it's not in the map
                hologramId = HOLOGRAM_PREFIX + location.getWorld().getName() + "_" + 
                        location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();

                // Check if the hologram exists
                if (DHAPI.getHologram(hologramId) == null) {
                    // Hologram doesn't exist, try to recreate it
                    UUID ownerUUID = MLVoidChest.getInstance().getChestManager().getChestOwner(location);
                    if (ownerUUID != null) {
                        createHologram(location, ownerUUID);
                        return; // createHologram already updates the countdown
                    } else {
                        return; // Can't recreate without owner
                    }
                }

                // Store the hologram ID for future use
                hologramIds.put(location, hologramId);
            }

            // Get the plugin instance
            MLVoidChest plugin = MLVoidChest.getInstance();

            // Check if countdown is enabled
            if (!plugin.getConfig().getBoolean("hologram.countdown.enabled", true)) {
                return;
            }

            // Get the collection interval from config
            int intervalSeconds = plugin.getConfig().getInt("collection-interval", 15);

            // Get the last collection time
            long lastCollectionTime = lastCollectionTimes.getOrDefault(location, System.currentTimeMillis());

            // Calculate time until next collection
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastCollectionTime;
            long remainingTime = Math.max(0, (intervalSeconds * 1000) - elapsedTime);

            // Convert to seconds
            int remainingSeconds = (int) (remainingTime / 1000);

            // If countdown has reached zero, reset the last collection time to start a new countdown
            if (remainingSeconds <= 0) {
                lastCollectionTimes.put(location, System.currentTimeMillis());
                remainingSeconds = intervalSeconds; // Reset to full interval
            }

            // Get format from config
            String format = plugin.getConfig().getString("hologram.countdown.format", "&7Next collection: &e%time%");

            // Format the countdown text (always show seconds now)
            String countdownText = MessageUtils.formatMessageNoPrefix(format.replace("%time%", remainingSeconds + "s"));

            // Update the hologram text (second line)
            Hologram hologram = DHAPI.getHologram(hologramId);
            if (hologram != null && hologram.getPage(0).getLines().size() > 1) {
                DHAPI.setHologramLine(hologram, 1, countdownText);
            }

        } catch (Exception e) {
            MLVoidChest.getInstance().getLogger().warning("Failed to update hologram countdown: " + e.getMessage());
        }
    }

    /**
     * Updates the last collection time for a chest
     *
     * @param location The location of the chest
     */
    public static void updateLastCollectionTime(Location location) {
        lastCollectionTimes.put(location, System.currentTimeMillis());
        updateHologramCountdown(location);
    }
}
