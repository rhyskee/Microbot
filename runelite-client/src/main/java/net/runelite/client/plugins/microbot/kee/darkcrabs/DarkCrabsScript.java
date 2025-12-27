package net.runelite.client.plugins.microbot.kee.darkcrabs;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.kee.darkcrabs.DarkCrabsInfo.botStatus;
import static net.runelite.client.plugins.microbot.kee.darkcrabs.DarkCrabsInfo.states;

@Slf4j
public class DarkCrabsScript extends Script {
    public static double version = 1.1;

public static final WorldPoint SAFE_SPOT = new WorldPoint(3184, 3925, 0);

    public boolean run(DarkCrabsConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        fishingLoop();
                        Rs2Antiban.takeMicroBreakByChance();
                        botStatus = states.NOTE_FISH;
                        Rs2Player.waitForAnimation();
                        break;
                    case NOTE_FISH:
                        useBank();
                        botStatus = states.FISHING;
                        Rs2Random.waitEx(400, 200);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void fishingLoop() {
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (Rs2Inventory.contains(ItemID.WILDERNESS_FISHING_BAIT)) {
                    Rs2Walker.walkTo(SAFE_SPOT);
                    Rs2Player.waitForWalking();
                    interactWithFishingSpot();
                    Rs2Player.waitForAnimation();
                    sleep(2000, 4000);
                } else {
                    Microbot.showMessage("Dark fishing bait not detected. Shutting down");
                    shutdown();
                    return;
                }
            }
        }
    }


    private void useBank() {
        Rs2Inventory.useItemOnNpc(11934, 13);
        Rs2Widget.sleepUntilHasWidget("Banknote");
        sleep(600, 1200);
        Rs2Keyboard.keyPress('1');
        Rs2Inventory.waitForInventoryChanges(1200);
            }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(1536, "Cage");
    }

}

