package eu.minelife.mLVoidChest;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.WildStacker;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import eu.minelife.mLVoidChest.commands.VoidChestCommand;
import eu.minelife.mLVoidChest.listeners.ChestListener;
import eu.minelife.mLVoidChest.listeners.PlayerListener;
import eu.minelife.mLVoidChest.managers.ChestManager;
import eu.minelife.mLVoidChest.managers.PlayerPreferenceManager;
import eu.minelife.mLVoidChest.tasks.HologramUpdateTask;
import eu.minelife.mLVoidChest.tasks.ItemCollectionTask;
import eu.minelife.mLVoidChest.utils.HologramUtils;

public final class MLVoidChest extends JavaPlugin implements Listener {
    private static MLVoidChest instance;
    private Economy economy;
    private ChestManager chestManager;
    private PlayerPreferenceManager preferenceManager;
    private BukkitTask collectionTask;
    private BukkitTask hologramUpdateTask;
    private File dataFolder;
    private File playersFolder;
    private boolean wildStackerWarningLogged = false;
    private boolean wildStackerInitialized = false;

    @Override
    public void onEnable() {
        // Set instance
        instance = this;

        // Create data folders
        dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        playersFolder = new File(dataFolder, "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }

        // Save default config
        saveDefaultConfig();

        // Setup economy
        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy plugin detected! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for ShopGUIPlus
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") == null) {
            getLogger().severe("ShopGUIPlus not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for WildStacker
        if (Bukkit.getPluginManager().getPlugin("WildStacker") == null) {
            getLogger().severe("WildStacker not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for DecentHolograms
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) {
            getLogger().severe("DecentHolograms not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        chestManager = new ChestManager(this);
        preferenceManager = new PlayerPreferenceManager(this);

        // Register commands
        getCommand("voidchest").setExecutor(new VoidChestCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(this, this); // Register this class as a listener for PluginEnableEvent

        // Log that the PlayerListener has been registered
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Registered PlayerListener to reload player data on join");
        }

        // Load player data
        chestManager.loadAllChestData();
        preferenceManager.loadAllPlayerPreferences();

        // Create holograms for existing chests
        if (getConfig().getBoolean("hologram.enabled", true)) {
            Set<Location> chestLocations = chestManager.getAllChestLocations();
            int createdCount = 0;
            for (Location location : chestLocations) {
                UUID ownerUUID = chestManager.getChestOwner(location);
                if (ownerUUID != null) {
                    boolean created = HologramUtils.createHologram(location, ownerUUID);
                    if (created) {
                        createdCount++;
                    } else {
                        getLogger().warning("Failed to create hologram for chest at " + 
                            location.getWorld().getName() + " " + 
                            location.getBlockX() + "," + 
                            location.getBlockY() + "," + 
                            location.getBlockZ());
                    }
                }
            }
            getLogger().info("Created holograms for " + createdCount + " out of " + chestLocations.size() + " existing void chests");
        }

        // Check if WildStacker is already initialized
        if (isWildStackerAPIInitialized()) {
            wildStackerInitialized = true;
            startCollectionTask();
        } else {
            getLogger().info("Waiting for WildStacker to initialize before starting collection task...");
        }

        getLogger().info("MLVoidChest has been enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (collectionTask != null) {
            collectionTask.cancel();
        }

        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
        }

        // Save all chest data
        if (chestManager != null) {
            chestManager.saveAllChestData();
        }

        // Save all player preferences
        if (preferenceManager != null) {
            preferenceManager.saveAllPlayerPreferences();
        }

        // Remove all holograms
        HologramUtils.removeAllHolograms();

        getLogger().info("MLVoidChest has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    public static MLVoidChest getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ChestManager getChestManager() {
        return chestManager;
    }

    public PlayerPreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    public File getPlayersFolder() {
        return playersFolder;
    }

    /**
     * Checks if the WildStacker API is initialized
     * 
     * @return true if the WildStacker API is initialized, false otherwise
     */
    public boolean isWildStackerAPIInitialized() {
        try {
            // First check if the WildStacker plugin is enabled
            if (!Bukkit.getPluginManager().isPluginEnabled("WildStacker")) {
                return false;
            }

            // Then try to access WildStacker's system manager
            WildStacker wildStacker = WildStackerAPI.getWildStacker();
            if (wildStacker == null) {
                return false;
            }

            // Finally, check if the system manager is available
            return wildStacker.getSystemManager() != null;
        } catch (Exception e) {
            // Any exception means the API is not initialized yet
            return false;
        }
    }

    /**
     * Logs a warning about the WildStacker API not being initialized, but only once
     */
    public void logWildStackerWarning() {
        if (!wildStackerWarningLogged) {
            getLogger().warning("WildStacker API not initialized yet, using regular item amount");
            wildStackerWarningLogged = true;
        }
    }

    /**
     * Starts the item collection task
     */
    private void startCollectionTask() {
        // Start collection task
        int interval = getConfig().getInt("collection-interval", 15) * 20; // Convert to ticks
        collectionTask = new ItemCollectionTask(this).runTaskTimer(this, 20, interval); // Start after 1 second
        getLogger().info("Started item collection task with interval: " + (interval / 20) + " seconds");

        // Start hologram update task (update every second)
        hologramUpdateTask = new HologramUpdateTask(this).runTaskTimer(this, 20, 20); // Update every second
        getLogger().info("Started hologram update task");
    }

    /**
     * Event handler for plugin enable events
     * Used to detect when WildStacker is fully initialized
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        // Check if the enabled plugin is WildStacker
        if (event.getPlugin().getName().equals("WildStacker")) {
            // Wait a short time to ensure WildStacker is fully initialized
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!wildStackerInitialized && isWildStackerAPIInitialized()) {
                    wildStackerInitialized = true;
                    getLogger().info("WildStacker API initialized, starting collection task...");
                    startCollectionTask();
                }
            }, 40); // Wait 2 seconds (40 ticks) after WildStacker is enabled
        }
    }
}
