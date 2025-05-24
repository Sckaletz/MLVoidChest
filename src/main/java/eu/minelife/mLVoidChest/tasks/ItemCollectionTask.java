package eu.minelife.mLVoidChest.tasks;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.WildStacker;
import com.bgsoftware.wildstacker.api.objects.StackedItem;
import eu.minelife.mLVoidChest.MLVoidChest;
import eu.minelife.mLVoidChest.utils.MessageUtils;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ItemCollectionTask extends BukkitRunnable {
    private final MLVoidChest plugin;

    public ItemCollectionTask(MLVoidChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Get all chest locations
        Set<Location> chestLocations = plugin.getChestManager().getAllChestLocations();

        // Process each chest
        for (Location chestLocation : chestLocations) {
            // Get the chest owner
            UUID ownerUUID = plugin.getChestManager().getChestOwner(chestLocation);
            if (ownerUUID == null) {
                continue;
            }

            // Get the chunk where the chest is located
            Chunk chunk = chestLocation.getChunk();

            // Collect and sell items in the chunk
            double groundItemsValue = collectAndSellItems(chunk, chestLocation, ownerUUID);

            // Since we're dealing with inventory operations, ensure we're on the main thread
            // for the chest inventory operations
            final UUID finalOwnerUUID = ownerUUID;
            final Location finalChestLocation = chestLocation.clone();

            // Use runTask to ensure we're on the main thread for inventory operations
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Collect and sell items from inside the chest
                double chestItemsValue = collectAndSellItemsFromChest(finalChestLocation, finalOwnerUUID);

                // Calculate total value (ground items + chest items)
                double totalValue = groundItemsValue + chestItemsValue;

                // Update the last collection time if items were sold
                if (totalValue > 0) {
                    // Update the last collection time for this chest
                    eu.minelife.mLVoidChest.utils.HologramUtils.updateLastCollectionTime(finalChestLocation);

                    // Notify the owner if they're online and if they have messages enabled
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(finalOwnerUUID);
                    if (owner.isOnline()) {
                        Player player = owner.getPlayer();
                        if (player != null && plugin.getPreferenceManager().hasMessagesEnabled(finalOwnerUUID)) {
                            String message = plugin.getConfig().getString("messages.items-sold", "&aYour Void Chest sold items for &e%amount%&a!");
                            message = message.replace("%amount%", String.format("%.2f", totalValue));
                            player.sendMessage(MessageUtils.formatMessage(message));
                        }
                    }
                }
            });
        }
    }

    /**
     * Collects and sells items in a chunk
     * 
     * @param chunk The chunk to collect items from
     * @param chestLocation The location of the void chest
     * @param ownerUUID The UUID of the chest owner
     * @return The total value of items sold
     */
    private double collectAndSellItems(Chunk chunk, Location chestLocation, UUID ownerUUID) {
        double totalValue = 0;

        // Get all entities in the chunk
        Entity[] entities = chunk.getEntities();

        // Process each entity
        for (Entity entity : entities) {
            // Check if the entity is an item
            if (!(entity instanceof Item)) {
                continue;
            }

            Item item = (Item) entity;
            ItemStack itemStack = item.getItemStack();

            // Check if the item has a price in ShopGuiPlus
            // Create a copy of the itemStack with amount 1 to get the price per item
            ItemStack singleItem = itemStack.clone();
            singleItem.setAmount(1);
            double pricePerItem = ShopGuiPlusApi.getItemStackPriceSell(singleItem);
            if (pricePerItem <= 0) {
                continue; // Skip items with no price
            }

            // Get the actual quantity (considering WildStacker)
            int quantity = itemStack.getAmount(); // Default to regular item amount

            // Only try to use WildStacker if it's properly initialized
            if (plugin.isWildStackerAPIInitialized()) {
                try {
                    WildStacker wildStacker = WildStackerAPI.getWildStacker();
                    if (wildStacker != null && wildStacker.getSystemManager() != null) {
                        StackedItem stackedItem = wildStacker.getSystemManager().getStackedItem(item);
                        if (stackedItem != null) {
                            quantity = stackedItem.getStackAmount();
                        }
                    }
                } catch (Exception e) {
                    // Silently fallback to regular item amount if WildStacker integration fails
                    // We already have the default quantity set above
                    plugin.getLogger().warning("Error getting stacked item amount: " + e.getMessage());
                }
            } else {
                // WildStackerAPI is not initialized yet, using regular item amount
                plugin.logWildStackerWarning();
            }

            // Calculate the total price for this item
            double itemValue = pricePerItem * quantity;
            totalValue += itemValue;

            // Add money to the player
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(ownerUUID), itemValue);

            // Remove the item from the world
            entity.remove();
        }

        return totalValue;
    }

    /**
     * Collects and sells items from inside a chest
     * 
     * @param chestLocation The location of the void chest
     * @param ownerUUID The UUID of the chest owner
     * @return The total value of items sold
     */
    private double collectAndSellItemsFromChest(Location chestLocation, UUID ownerUUID) {
        double totalValue = 0;

        try {
            // Get the block at the chest location
            Block block = chestLocation.getBlock();

            // Check if the block is a chest
            if (block.getType() != Material.CHEST) {
                return 0;
            }

            // Get the chest state
            Chest chest = (Chest) block.getState();

            // Get the chest inventory
            Inventory inventory = chest.getInventory();

            // Create a list to store items that need to be processed
            List<ItemStack> itemsToProcess = new ArrayList<>();
            Map<Integer, ItemStack> slotMap = new HashMap<>();

            // First pass: identify items to process and calculate total value
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemStack = inventory.getItem(i);

                // Skip empty slots
                if (itemStack == null || itemStack.getType() == Material.AIR) {
                    continue;
                }

                // Check if the item has a price in ShopGuiPlus
                ItemStack singleItem = itemStack.clone();
                singleItem.setAmount(1);
                double pricePerItem = ShopGuiPlusApi.getItemStackPriceSell(singleItem);

                if (pricePerItem <= 0) {
                    continue; // Skip items with no price
                }

                // Store the item for processing
                itemsToProcess.add(itemStack.clone());
                slotMap.put(i, itemStack.clone());

                // Get the quantity
                int quantity = itemStack.getAmount();

                // Calculate the total price for this item
                double itemValue = pricePerItem * quantity;
                totalValue += itemValue;

                // Add money to the player
                plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(ownerUUID), itemValue);
            }

            // If we have items to process, clear the entire inventory
            if (!itemsToProcess.isEmpty()) {
                try {
                    // Try multiple methods to clear the inventory
                    // Method 1: Clear the entire inventory
                    inventory.clear();

                    // Method 2: Set all slots to null or AIR
                    for (int i = 0; i < inventory.getSize(); i++) {
                        inventory.setItem(i, null);
                    }

                    // Method 3: Remove all items one by one
                    for (ItemStack item : itemsToProcess) {
                        inventory.removeItem(item);
                    }

                    // Method 4: Get a fresh chest state and clear it
                    Chest freshChest = (Chest) block.getState();
                    Inventory freshInventory = freshChest.getInventory();
                    freshInventory.clear();

                    // Method 5: Try to replace problematic slots with air directly
                    for (int slot : slotMap.keySet()) {
                        freshInventory.setItem(slot, new ItemStack(Material.AIR));
                    }

                    // Force update the chest state
                    chest.update(true, true);
                    freshChest.update(true, true);

                    // Method 6: Schedule an immediate task to clear the inventory again
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            Block currentBlock = chestLocation.getBlock();
                            if (currentBlock.getType() == Material.CHEST) {
                                Chest currentChest = (Chest) currentBlock.getState();
                                Inventory currentInventory = currentChest.getInventory();
                                currentInventory.clear();
                                currentChest.update(true, true);
                            }
                        } catch (Exception e) {
                            // Error handling for immediate clearing task
                        }
                    });
                } catch (Exception e) {
                    // Error handling for chest inventory clearing
                }
            }

            // Additional verification to ensure items are removed
            if (!slotMap.isEmpty()) {
                // Schedule a task to perform additional clearing if needed
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        // Get a fresh state of the chest
                        Chest verifyChest = (Chest) block.getState();
                        Inventory verifyInventory = verifyChest.getInventory();

                        // Try to clear the entire inventory again
                        verifyInventory.clear();

                        // Force update
                        verifyChest.update(true, true);

                        // Final extreme measure to ensure chest is cleared
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            try {
                                Block freshBlock = chestLocation.getBlock();
                                if (freshBlock.getType() == Material.CHEST) {
                                    Chest finalChest = (Chest) freshBlock.getState();
                                    Inventory finalInventory = finalChest.getInventory();

                                    // Clear the inventory one more time
                                    finalInventory.clear();
                                    finalChest.update(true, true);
                                }
                            } catch (Exception e) {
                                // Error handling for final verification
                            }
                        }, 20L); // Check after 1 second (20 ticks)
                    } catch (Exception e) {
                        // Error handling for verification task
                    }
                }, 5L); // Check after 5 ticks (0.25 seconds)
            }

        } catch (Exception e) {
            // Error handling for selling items from chest
        }

        return totalValue;
    }
}
