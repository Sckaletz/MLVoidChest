package eu.minelife.mLVoidChest.tasks;

import eu.minelife.mLVoidChest.MLVoidChest;
import eu.minelife.mLVoidChest.utils.HologramUtils;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;

/**
 * Task to update hologram countdowns for all void chests
 */
public class HologramUpdateTask extends BukkitRunnable {
    private final MLVoidChest plugin;

    public HologramUpdateTask(MLVoidChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Get all chest locations
        Set<Location> chestLocations = plugin.getChestManager().getAllChestLocations();

        // Update the countdown for each chest
        for (Location location : chestLocations) {
            HologramUtils.updateHologramCountdown(location);
        }
    }
}