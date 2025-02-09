package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Amethyst;
import net.runelite.client.plugins.microbot.crafting.enums.Glass;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.naming.Name;
import java.util.concurrent.TimeUnit;

public class AmethystDarts extends Script {

    public static double version = 2.0;

    int amethyst = 21347;
    int chiselId = 1755;
    String chisel = "chisel";
    Amethyst itemToCraft;

    public void run(CraftingConfig config) {

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (Rs2Player.isAnimating(3000) || Rs2Antiban.getCategory().isBusy() || Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                itemToCraft = config.amethystType();

                if (Rs2Inventory.hasItem(amethyst) && Rs2Inventory.hasItem(chisel)) {
                    craft(config);
                    return;
                }
                if (!Rs2Inventory.hasItem(amethyst) || !Rs2Inventory.hasItem(chisel)) {
                    bank(config);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);

        Rs2Bank.withdrawItem(true, chisel);
        sleepUntil(() -> Rs2Inventory.hasItem(chisel));

        verifyItemInBank("amethyst");

        Rs2Bank.withdrawAll(amethyst);
        sleepUntil(() -> Rs2Inventory.hasItem(amethyst));

        Rs2Bank.closeBank();
    }

    private void verifyItemInBank(String item) {
        if (Rs2Bank.isOpen() && !Rs2Bank.hasItem(item)) {
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + item + " not found in the bank.";
            Microbot.getNotifier().notify(Microbot.status);
            shutdown();
        }
    }

    private void craft(CraftingConfig config) {
        Rs2Inventory.combine(chiselId, amethyst);

        Rs2Widget.sleepUntilHasWidgetText("How many do you wish to make?", 270, 5, false, 5000);

        keyPress(itemToCraft.getMenuEntry());

        Rs2Widget.sleepUntilHasNotWidgetText("How many do you wish to make?", 270, 5, false, 5000);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
