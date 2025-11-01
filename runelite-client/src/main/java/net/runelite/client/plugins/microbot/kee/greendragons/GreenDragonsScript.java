package net.runelite.client.plugins.microbot.kee.greendragons;

import lombok.Getter;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.kee.greendragons.GreenDragonState;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GreenDragonsScript extends Script {

    public static GreenDragonState currentState;
    private String lastChatMessage = "";
    private GreenDragonsConfig config;
    public static final WorldPoint SAFE_SPOT = new WorldPoint(1943, 8995, 1);
    @Getter
    private Integer currentTargetId = null;

    @Inject
    private GreenDragonsOverlay overlay;
    
    private long lastLootMessageTime = 0;

    private static final int BLUE_DRAGON_ID_1 = 13010;
    private static final int BLUE_DRAGON_ID_2 = 13488;
    //private static final int BLUE_DRAGON_ID_3 = 267;
    private static final int MIN_WORLD = 302;
    private static final int MAX_WORLD = 580;
    public static final int mythicStatueRegion = 9772;
    public static final int mythCape = ItemID.MYTHICAL_CAPE;

    private boolean isInventoryFull() {
        boolean simpleFull = Rs2Inventory.isFull();
        if (!simpleFull) {
            return Rs2Inventory.getEmptySlots() <= 0;
        }
        
        return true;
    }

    public boolean run(GreenDragonsConfig config) {
        this.config = config;
        currentState = GreenDragonState.STARTING;
        
        if (overlay != null) {
            overlay.resetStats();
            overlay.setScript(this);
            overlay.setConfig(config);
        }
        
        final int[] consecutiveErrors = {0};
        final int MAX_CONSECUTIVE_ERRORS = 5;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (isInventoryFull() && currentState != GreenDragonState.BANKING && currentState != GreenDragonState.STARTING) {
                    logOnceToChat("Global safety: Inventory is full. Switching to BANKING state.", false, config);
                    currentState = GreenDragonState.BANKING;
                }

                switch (currentState) {
                    case STARTING:
                        determineStartingState(config);
                        break;

                    case BANKING:
                        handleBanking(config);
                        break;

                    case TRAVEL_TO_DRAGONS:
                        handleTravelToDragons();
                        break;

                    case FIGHTING:
                        handleFighting(config);
                        break;
                        
                    case LOOTING:
                        handleLooting(config);
                        break;
                }
                
                consecutiveErrors[0] = 0;
                
            } catch (Exception ex) {
                consecutiveErrors[0]++;
                logOnceToChat("Error in Blue Dragons script: " + ex.getMessage(), false, config);
                
                if (config.debugLogs()) {
                    StringBuilder stackTrace = new StringBuilder();
                    for (StackTraceElement element : ex.getStackTrace()) {
                        stackTrace.append(element.toString()).append("\n");
                    }
                    logOnceToChat("Stack trace: " + stackTrace.toString(), true, config);
                }
                
                if (consecutiveErrors[0] >= MAX_CONSECUTIVE_ERRORS) {
                    logOnceToChat("Too many consecutive errors. Stopping script for safety.", false, config);
                    shutdown();
                }
                
                if (Rs2Player.isInCombat()) {
                    logOnceToChat("In combat during error - attempting to eat food", true, config);
                    Rs2Player.eatAt(config.eatAtHealthPercent());
                }
                
                sleep(1000 * Math.min(consecutiveErrors[0], 5));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleBanking(GreenDragonsConfig config) {
        logOnceToChat("Traveling to guild bank for depositing looted items.", true, config);
        logOnceToChat("Current location: " + Microbot.getClient().getLocalPlayer().getWorldLocation(), true, config);

        if (Rs2Inventory.contains(mythCape)) {
            Microbot.log("Interacting with myth cape");
            Rs2Inventory.interact(mythCape, "Teleport");
            sleepGaussian(7000, 2500);

        if (Rs2GameObject.exists(31627)) {
            Rs2GameObject.interact(31627);
            sleepGaussian(7000, 2500);
        }

        if (Rs2Bank.walkToBankAndUseBank(BankLocation.MYTHS_GUILD))
            logOnceToChat("Opened bank. Depositing loot.", true, config);
            // Core drops
            Rs2Bank.depositAll("Green dragonhide");
            Rs2Bank.depositAll("Dragon bones");
            Rs2Bank.depositAll("Scaly blue dragonhide");
            Rs2Bank.depositAll("Ensouled dragon head");


            logOnceToChat("Withdrawing food for combat.", true, config);
            withdrawFood(config);
            Rs2Bank.closeBank();
            logOnceToChat("Banking complete. Transitioning to travel state.", true, config);
            currentState = GreenDragonState.TRAVEL_TO_DRAGONS;
        }
        else {
            logOnceToChat("Failed to reach the bank.", true, config);
        }
    }

    private void determineStartingState(GreenDragonsConfig config) {
        boolean hasTeleport = hasCape();

        if (!hasTeleport) {
            logOnceToChat("Missing teleport to cape or required runes.", false, config);
        }
        if (hasTeleport) {
            currentState = GreenDragonState.BANKING;
        }
        else {
            logOnceToChat("Starting conditions not met. Stopping the plugin.", false, config);
            shutdown();
        }
    }

    private boolean hasCape() {
        logOnceToChat("Checking for myths cape", true, config);

        if (Rs2Inventory.contains(mythCape)) {
            logOnceToChat("Found cape teleport in inventory.", true, config);
            return true;
        }
        else {
            return false;
        }
    }

    private void handleTravelToDragons() {
        logOnceToChat("Traveling to dragons.", false, config);
        logOnceToChat("Player location before travel: " + Microbot.getClient().getLocalPlayer().getWorldLocation(), true, config);

        if (isPlayerAtSafeSpot()) {
            logOnceToChat("Already at safe spot. Transitioning to FIGHTING state.", true, config);
            currentState = GreenDragonState.FIGHTING;
            return;
        }

        boolean walkAttemptSuccessful = Rs2Walker.walkTo(SAFE_SPOT, 0);
        
        if (!walkAttemptSuccessful) {
            logOnceToChat("Failed to start walking to safe spot. Will retry next tick.", true, config);
            return;
        }
        
        boolean reachedNearSafeSpot = sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 20, 60000);
        
        if (reachedNearSafeSpot || Rs2Player.distanceTo(SAFE_SPOT) <= 20) {
            logOnceToChat("Close to safe spot. Using precise movement for final approach.", true, config);
            moveToSafeSpot();
        } else {
            logOnceToChat("Failed to get close to safe spot within timeout.", true, config);
            
            int distance = Rs2Player.distanceTo(SAFE_SPOT);
            logOnceToChat("Current distance to safe spot: " + distance, true, config);
            
            if (distance < 50) {
                moveToSafeSpot();
            } else {
                logOnceToChat("Too far from safe spot. Returning to banking state to try again.", true, config);
                currentState = GreenDragonState.BANKING;
                return;
            }
        }

        if (hopIfPlayerAtSafeSpot()) {
            logOnceToChat("Hopped worlds due to player detection at safe spot.", true, config);
            return;
        }
        
        if (isPlayerAtSafeSpot()) {
            logOnceToChat("Reached safe spot. Transitioning to FIGHTING state.", true, config);
            currentState = GreenDragonState.FIGHTING;
        } else {
            logOnceToChat("Still not at safe spot after multiple attempts. Will continue from current position.", true, config);
            currentState = GreenDragonState.FIGHTING;
        }
    }

    private void handleFighting(GreenDragonsConfig config) {
        if (currentState != GreenDragonState.FIGHTING) {
            logOnceToChat("Not in FIGHTING state but handleFighting was called. Current state: " + currentState, true, config);
            return;
        }
        
        if (isInventoryFull()) {
            logOnceToChat("Inventory is full. Switching to BANKING state.", false, config);
            currentState = GreenDragonState.BANKING;
            return;
        }
        
        if (checkForLoot()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLootMessageTime > 10000) {
                logOnceToChat("Found loot on the ground. Switching to LOOTING state.", false, config);
                lastLootMessageTime = currentTime;
            }
            currentState = GreenDragonState.LOOTING;
            return;
        }

        Rs2Player.eatAt(config.eatAtHealthPercent());

        if (!underAttack()) {
            Rs2NpcModel dragon = getAvailableDragon();
            if (dragon != null) {
                logOnceToChat("Found available dragon. Attacking.", true, config);
                if (attackDragon(dragon)) {
                    currentTargetId = dragon.getId();
                }
            } else {
                logOnceToChat("No dragons available to attack.", true, config);
            }
        } else {
            if (!isPlayerAtSafeSpot()) {
                moveToSafeSpot();
            }
        }
    }
    
    private boolean checkForLoot() {
        String[] lootItems = {
            "Dragon bones", "Green dragonhide", "Ensouled dragon head",
            "Dragon spear", "Shield left half"
        };
        
        LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, lootItems);
        
        return Rs2GroundItem.lootItemsBasedOnNames(params);
    }
    
    private void handleLooting(GreenDragonsConfig config) {


        if (currentState != GreenDragonState.LOOTING) {
            return;
        }
        
        if (isInventoryFull() && !Rs2Inventory.contains(3144)) {
            logOnceToChat("Inventory is full, switching to BANKING state.", false, config);
            currentState = GreenDragonState.BANKING;
            return;
        }
        if (isInventoryFull() && Rs2Inventory.contains(3144)) {
            Rs2Inventory.interact(3144, "Eat");
            return;
        }

        
        boolean lootedAnything = false;
        
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon bones");
        } 
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon spear");
        }
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Shield left half");
        }

        if (!isInventoryFull() && config.lootDragonhide()) {
            lootedAnything |= lootItem("Green dragonhide");
        }
        
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Ensouled dragon head");
        }
        
        sleep(300, 500);
        
        if (isInventoryFull()) {
            logOnceToChat("Inventory is full after looting, switching to BANKING state.", false, config);
            currentState = GreenDragonState.BANKING;
            return;
        }
        
        if (!checkForLoot() || !lootedAnything) {
            logOnceToChat("Finished looting. Returning to combat.", true, config);
            currentState = GreenDragonState.FIGHTING;
            currentTargetId = null;
        }
    }
    
    private boolean lootItem(String itemName) {
        if (!isInventoryFull()) {
            LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, itemName);
            boolean looted = Rs2GroundItem.lootItemsBasedOnNames(params);
            if (looted) {
                logOnceToChat("Looted: " + itemName, true, config);
                
                if (itemName.equalsIgnoreCase("Dragon bones")) {
                    GreenDragonsOverlay.bonesCollected++;
                    Microbot.log("Bones looted: " + GreenDragonsOverlay.bonesCollected);
                } else if (itemName.equalsIgnoreCase("Blue dragonhide")) {
                    GreenDragonsOverlay.hidesCollected++;
                }
                
                return true;
            }
        }
        return false;
    }

    private void withdrawFood(GreenDragonsConfig config) {
        Rs2Food food = config.foodType();
        int requiredAmount = config.foodAmount();

        if (food == null || requiredAmount <= 0) {
            logOnceToChat("Invalid food type or amount in configuration.", true, config);
            return;
        }

        int currentFoodInInventory = Rs2Inventory.count(food.getName());
        int deficit = requiredAmount - currentFoodInInventory;

        if (deficit <= 0) {
            logOnceToChat("Inventory already contains the required amount of " + food.getName() + ".", true, config);
            return;
        }

        if (!Rs2Bank.isOpen()) {
            logOnceToChat("Bank is not open. Cannot withdraw food.", true, config);
            return;
        }

        boolean bankLoaded = sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), 5000);
        if (!bankLoaded) {
            logOnceToChat("Bank items did not load in time.", true, config);
            return;
        }

        if (!Rs2Bank.hasItem(food.getName())) {
            logOnceToChat(food.getName() + " not found in the bank. Stopping script.", true, config);
            shutdown();
            return;
        }

        logOnceToChat("Attempting to withdraw " + deficit + "x " + food.getName(), false, config);

        int retryCount = 0;
        final int maxRetries = 3;
        boolean success = false;

        while (retryCount < maxRetries && !success) {
            success = Rs2Bank.withdrawX(food.getName(), deficit, true);
            if (!success) {
                retryCount++;
                sleep(500);
                logOnceToChat("Retrying withdrawal of " + food.getName() + " (" + retryCount + ")", true, config);
            }
        }

        if (!success) {
            logOnceToChat("Unable to withdraw " + food.getName() + " after multiple attempts. Stopping script.", true, config);
            shutdown();
        } else {
            logOnceToChat("Successfully withdrew " + deficit + "x " + food.getName(), false, config);
        }
    }

    private Rs2NpcModel getAvailableDragon() {
        Rs2NpcModel dragon = Rs2Npc.getNpc("Green dragon");
        logOnceToChat("Found dragon: " + (dragon != null ? "Yes (ID: " + dragon.getId() + ")" : "No"), true, config);
        
        if (dragon != null) {
            boolean correctId = (dragon.getId() == BLUE_DRAGON_ID_1 || dragon.getId() == BLUE_DRAGON_ID_2 );
            logOnceToChat("Dragon has correct ID (265, 266, or 267): " + correctId, true, config);
            
            boolean hasLineOfSight = Rs2Npc.hasLineOfSight(new Rs2NpcModel(dragon));
            logOnceToChat("Has line of sight to dragon: " + hasLineOfSight, true, config);
            
            if (correctId && hasLineOfSight) {
                return dragon;
            }
        }
        return null;
    }

    private boolean attackDragon(Rs2NpcModel dragon) {
        final int dragonId = dragon.getId();
        
        if (Rs2Combat.inCombat() && dragon.getInteracting() != Microbot.getClient().getLocalPlayer()) {
            logOnceToChat("Cannot attack dragon - player is in combat with different target.", true, config);
            return false;
        }
        
        if (Rs2Npc.attack(dragon)) {
            boolean dragonKilled = sleepUntil(() -> Rs2Npc.getNpc(dragonId) == null, 60000);
            
            if (dragonKilled) {
                logOnceToChat("Dragon killed. Transitioning to looting state.", true, config);
                GreenDragonsOverlay.dragonKillCount++;
                
                sleep(600, 900);
                currentState = GreenDragonState.LOOTING;
            }
            
            return true;
        }
        return false;
    }

    private boolean isPlayerAtSafeSpot() {
        return SAFE_SPOT.equals(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    private void moveToSafeSpot() {
		Microbot.pauseAllScripts.compareAndSet(false, true);
        
        int distance = Rs2Player.distanceTo(SAFE_SPOT);
        
        logOnceToChat("Moving to safe spot. Distance: " + distance, true, config);
        
        if (distance > 15) {
            logOnceToChat("Using walkTo to approach safe spot", true, config);
            Rs2Walker.walkTo(SAFE_SPOT, 0);
            
            sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 30000);
        }
        
        if (!isPlayerAtSafeSpot()) {
            logOnceToChat("Using walkFastCanvas for final approach to safe spot", true, config);
            Rs2Walker.walkFastCanvas(SAFE_SPOT);
            sleepUntil(this::isPlayerAtSafeSpot, 15000);
        }

        if (hopIfPlayerAtSafeSpot()) {
            return;
        }

        if (!isPlayerAtSafeSpot()) {
            logOnceToChat("Failed to reach exact safe spot. Will continue with current position.", true, config);
        } else {
            logOnceToChat("Successfully reached safe spot.", true, config);
        }

		Microbot.pauseAllScripts.compareAndSet(true, false);
    }

    private boolean hopIfPlayerAtSafeSpot() {
        boolean otherPlayersAtSafeSpot = false;
        List<Player> players = Rs2Player.getPlayers(it->it!=null).collect(Collectors.toList());
        for (Player player : players) {
            if (player != null &&
                !player.equals(Microbot.getClient().getLocalPlayer()) &&
                player.getWorldLocation().distanceTo(SAFE_SPOT) <= 15) {
                otherPlayersAtSafeSpot = true;
                break;
            }
        }
                
        if (otherPlayersAtSafeSpot) {
            logOnceToChat("Player detected at safe spot. Pausing script and hopping worlds.", false, config);
            Microbot.pauseAllScripts.set(true);
            
            boolean hopSuccess = Microbot.hopToWorld(findRandomWorld());
            sleep(5000);
            
            Microbot.pauseAllScripts.set(false);
            return hopSuccess;
        }
        
        return false;
    }
    
    private int findRandomWorld() {
        int currentWorld = Microbot.getClient().getWorld();
        int targetWorld;
        do {
            targetWorld = MIN_WORLD + new java.util.Random().nextInt(MAX_WORLD - MIN_WORLD);
        } while (targetWorld == currentWorld);
        return targetWorld;
    }

    void logOnceToChat(String message, boolean isDebug, GreenDragonsConfig config) {
        if (message == null || message.trim().isEmpty()) {
            message = "Unknown log message (null or empty)...";
        }
        if (isDebug && (config == null || !config.debugLogs())) {
            return;
        }
        if (!message.equals(lastChatMessage)) {
            Microbot.log(message);
            lastChatMessage = message;
        }
    }

    public void updateConfig(GreenDragonsConfig config) {
        logOnceToChat("Applying new configuration to Blue Dragons script.", true, config);
        this.config = config;
        
        if (overlay != null) {
            overlay.setConfig(config);
        }
        
        withdrawFood(config);
    }

    public void shutdown() {
        super.shutdown();
        Rs2Walker.disableTeleports = false;
        if (overlay != null) {
            overlay.resetStats();
        }
        currentState = GreenDragonState.STARTING;
        currentTargetId = null;
    }

    private boolean underAttack() {
        return Rs2Player.isAnimating(5000);
    }
}