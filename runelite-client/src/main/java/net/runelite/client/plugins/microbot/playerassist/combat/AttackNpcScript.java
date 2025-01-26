package net.runelite.client.plugins.microbot.playerassist.combat;

import net.runelite.api.Actor;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.playerassist.PlayerAssistConfig;
import net.runelite.client.plugins.microbot.playerassist.PlayerAssistPlugin;
import net.runelite.client.plugins.microbot.playerassist.enums.AttackStyle;
import net.runelite.client.plugins.microbot.playerassist.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.playerassist.enums.State;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AttackNpcScript extends Script {

    public static Actor currentNpc = null;
    public static List<NPC> attackableNpcs = new ArrayList<>();
    private boolean messageShown = false;

    public static void skipNpc() {
        currentNpc = null;
    }

    public void run(PlayerAssistConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();

        Rs2Antiban.takeMicroBreakByChance();

        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !config.toggleCombat())
                    return;

                if(config.state().equals(State.BANKING))
                    return;

                List<String> npcsToAttack = Arrays.stream(config.attackableNpcs().split(","))
                        .map(x -> x.trim().toLowerCase())
                        .collect(Collectors.toList());

                double healthPercentage = (double) Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100
                        / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                if (Rs2Inventory.getInventoryFood().isEmpty() && healthPercentage < 10)
                    return;

                if (config.toggleCenterTile() && config.centerLocation().getX() == 0
                        && config.centerLocation().getY() == 0) {
                    if (!messageShown) {
                        Microbot.showMessage("Please set a center location");
                        messageShown = true;
                    }
                    return;
                }
                messageShown = false;

                attackableNpcs = Rs2Npc.getAttackableNpcs(config.attackReachableNpcs())
                        .filter(npc -> npc.getWorldLocation().distanceTo(config.centerLocation()) <= config.attackRadius()
                                && npcsToAttack.contains(npc.getName().toLowerCase()))
                        .sorted(Comparator
                                .comparing((NPC npc) -> npc.getInteracting() == Microbot.getClient().getLocalPlayer() ? 0 : 1).thenComparingInt(npc -> Rs2Player.getRs2WorldPoint().distanceToPath(npc.getWorldLocation())))
                        .collect(Collectors.toList());

                if (PlayerAssistPlugin.getCooldown() > 0 || Rs2Combat.inCombat()) {
                    PlayerAssistPlugin.setState(State.COMBAT);
                    handleItemOnNpcToKill();
                    return;
                }

                if (!attackableNpcs.isEmpty()) {
                    NPC npc = attackableNpcs.stream().findFirst().orElse(null);

                    if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))
                        Rs2Camera.turnTo(npc);

                    Rs2Npc.interact(npc, "attack");
                    Microbot.status = "Attacking " + npc.getName();
                    PlayerAssistPlugin.setCooldown(config.playStyle().getRandomTickInterval());
                    sleepUntil(Rs2Player::isInteracting, 1000);
//                    sleepUntil(() -> Microbot.getClient().getLocalPlayer().isInteracting()
//                            && Microbot.getClient().getLocalPlayer().getInteracting() instanceof NPC);

                    if (config.togglePrayer()) {
                        if (!config.toggleQuickPray()) {
                            AttackStyle attackStyle = AttackStyleMapper
                                    .mapToAttackStyle(Rs2NpcManager.getAttackStyle(npc.getId()));
                            if (attackStyle != null) {
                                switch (attackStyle) {
                                    case MAGE:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                                        break;
                                    case MELEE:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                                        break;
                                    case RANGED:
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                                        break;
                                }
                            }
                        } else {
                            Rs2Prayer.toggleQuickPrayer(true);
                        }
                    }


                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }


    /**
     * item on npcs that need to kill like rockslug
     */
    private void handleItemOnNpcToKill() {
        NPC npc = Rs2Npc.getNpcsAttackingPlayer(Microbot.getClient().getLocalPlayer()).stream().findFirst().orElse(null);
        if (npc.getName().equalsIgnoreCase("desert lizard") && npc.getHealthRatio() < 10 && !npc.isDead()) {
            Rs2Inventory.useItemOnNpc(ItemID.ICE_COOLER, npc);
            Rs2Player.waitForAnimation();
        } else if (npc.getName().equalsIgnoreCase("rockslug") && npc.getHealthRatio() < 10 && !npc.isDead()) {
            Rs2Inventory.useItemOnNpc(ItemID.BAG_OF_SALT, npc);
            Rs2Player.waitForAnimation();
        } else if (npc.getName().equalsIgnoreCase("gargoyle") && npc.getHealthRatio() < 10 && !npc.isDead()) {
            Rs2Inventory.useItemOnNpc(ItemID.ROCK_HAMMER, npc);
            Rs2Player.waitForAnimation();
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
