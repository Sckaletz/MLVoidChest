package eu.minelife.mLVoidChest.managers;

import eu.minelife.mLVoidChest.MLVoidChest;
import eu.minelife.mLVoidChest.utils.HologramUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ChestManager {
    private final MLVoidChest plugin;
    private final Map<UUID, Set<Location>> playerChests;
    private final Map<Location, UUID> chestOwners;
    private final Map<String, Location> chunkChests; // Map to track one chest per chunk

    public ChestManager(MLVoidChest plugin) {
        this.plugin = plugin;
        this.playerChests = new HashMap<>();
        this.chestOwners = new HashMap<>();
        this.chunkChests = new HashMap<>();
    }

    /**
     * Gets a unique string key for a chunk
     * 
     * @param chunk The chunk
     * @return A unique string key for the chunk
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    /**
     * Checks if a chunk already has a void chest
     * 
     * @param chunk The chunk to check
     * @return true if the chunk already has a chest, false otherwise
     */
    public boolean hasChestInChunk(Chunk chunk) {
        return chunkChests.containsKey(getChunkKey(chunk));
    }

    /**
     * Adds a chest to a player's collection
     * 
     * @param player The player who owns the chest
     * @param location The location of the chest
     * @return true if the chest was added, false if the player has reached their limit or the chunk already has a chest
     */
    public boolean addChest(Player player, Location location) {
        UUID playerUUID = player.getUniqueId();

        // Check if player has reached their limit
        int limit = getPlayerChestLimit(player);
        if (limit != -1 && getPlayerChestCount(playerUUID) >= limit) {
            return false;
        }

        // Check if the chunk already has a chest
        Chunk chunk = location.getChunk();
        String chunkKey = getChunkKey(chunk);
        if (chunkChests.containsKey(chunkKey)) {
            return false;
        }

        // Add chest to player's collection
        Set<Location> chests = playerChests.computeIfAbsent(playerUUID, k -> new HashSet<>());
        chests.add(location);

        // Register chest owner
        chestOwners.put(location, playerUUID);

        // Register chest in chunk
        chunkChests.put(chunkKey, location);

        // Save player data
        savePlayerData(playerUUID);

        // Create hologram above the chest
        HologramUtils.createHologram(location, playerUUID);

        return true;
    }

    /**
     * Removes a chest from a player's collection
     * 
     * @param location The location of the chest
     * @return true if the chest was removed, false if it wasn't found
     */
    public boolean removeChest(Location location) {
        UUID ownerUUID = chestOwners.remove(location);
        if (ownerUUID == null) {
            return false;
        }

        // Remove from player's collection
        Set<Location> chests = playerChests.get(ownerUUID);
        if (chests != null) {
            chests.remove(location);
            savePlayerData(ownerUUID);
        }

        // Remove from chunk mapping
        Chunk chunk = location.getChunk();
        String chunkKey = getChunkKey(chunk);
        chunkChests.remove(chunkKey);

        // Remove hologram above the chest
        HologramUtils.removeHologram(location);

        return true;
    }

    /**
     * Gets the owner of a chest
     * 
     * @param location The location of the chest
     * @return The UUID of the chest owner, or null if not found
     */
    public UUID getChestOwner(Location location) {
        return chestOwners.get(location);
    }

    /**
     * Gets the number of chests a player has placed
     * 
     * @param playerUUID The UUID of the player
     * @return The number of chests the player has placed
     */
    public int getPlayerChestCount(UUID playerUUID) {
        Set<Location> chests = playerChests.get(playerUUID);
        return chests == null ? 0 : chests.size();
    }

    /**
     * Gets all chest locations owned by a player
     * 
     * @param playerUUID The UUID of the player
     * @return A set of chest locations, or an empty set if none
     */
    public Set<Location> getPlayerChests(UUID playerUUID) {
        return playerChests.getOrDefault(playerUUID, new HashSet<>());
    }

    /**
     * Gets all chest locations in the system
     * 
     * @return A set of all chest locations
     */
    public Set<Location> getAllChestLocations() {
        return new HashSet<>(chestOwners.keySet());
    }

    /**
     * Gets the maximum number of chests a player can place
     * 
     * @param player The player to check
     * @return The maximum number of chests, or -1 for unlimited
     */
    public int getPlayerChestLimit(Player player) {
        if (player.hasPermission("mlvoidchest.place.unlimited")) {
            return -1;
        }

        for (int i = 10; i > 0; i--) {
            if (player.hasPermission("mlvoidchest.place." + i)) {
                return i;
            }
        }

        return 0; // No permission to place any chests
    }

    /**
     * Saves a player's chest data to file
     * 
     * @param playerUUID The UUID of the player
     */
    public void savePlayerData(UUID playerUUID) {
        File playerFile = new File(plugin.getPlayersFolder(), playerUUID.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        Set<Location> chests = playerChests.get(playerUUID);
        if (chests != null && !chests.isEmpty()) {
            List<String> chestLocations = new ArrayList<>();

            for (Location location : chests) {
                String locString = location.getWorld().getName() + "," +
                        location.getBlockX() + "," +
                        location.getBlockY() + "," +
                        location.getBlockZ();
                chestLocations.add(locString);
            }

            config.set("chests", chestLocations);

            try {
                config.save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + playerUUID, e);
            }
        } else {
            // No chests, delete the file if it exists
            if (playerFile.exists()) {
                playerFile.delete();
            }
        }
    }

    /**
     * Loads a player's chest data from file
     * 
     * @param playerUUID The UUID of the player
     */
    public void loadPlayerData(UUID playerUUID) {
        File playerFile = new File(plugin.getPlayersFolder(), playerUUID.toString() + ".yml");
        if (!playerFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        List<String> chestLocations = config.getStringList("chests");

        Set<Location> chests = new HashSet<>();

        for (String locString : chestLocations) {
            String[] parts = locString.split(",");
            if (parts.length != 4) {
                continue;
            }

            try {
                String worldName = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                if (Bukkit.getWorld(worldName) != null) {
                    Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
                    chests.add(location);
                    chestOwners.put(location, playerUUID);

                    // Also register in chunk mapping
                    Chunk chunk = location.getChunk();
                    String chunkKey = getChunkKey(chunk);
                    chunkChests.put(chunkKey, location);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid location format in player data: " + locString);
            }
        }

        if (!chests.isEmpty()) {
            playerChests.put(playerUUID, chests);
        }
    }

    /**
     * Loads all player chest data
     */
    public void loadAllChestData() {
        File[] playerFiles = plugin.getPlayersFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null) {
            return;
        }

        for (File file : playerFiles) {
            String fileName = file.getName();
            if (fileName.endsWith(".yml")) {
                try {
                    UUID playerUUID = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                    loadPlayerData(playerUUID);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid player data file name: " + fileName);
                }
            }
        }
    }

    /**
     * Saves all player chest data
     */
    public void saveAllChestData() {
        for (UUID playerUUID : playerChests.keySet()) {
            savePlayerData(playerUUID);
        }
    }
}
