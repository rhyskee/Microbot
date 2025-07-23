package net.runelite.client.plugins.microbot.thieving;

import com.google.inject.Inject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.security.Login;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ThievingScript extends Script {
    private final ThievingConfig config;
    private final ThievingPlugin plugin;
    private static final int DARKMEYER_REGION = 14388;
    private enum State {IDLE, BANK, PICKPOCKET}
    public State currentState = State.IDLE;

    private static final Set<String> VYRE_SET = Set.of(
        "Vyre noble shoes",
        "Vyre noble legs",
        "Vyre noble top"
    );
    private static final Set<String> ROGUE_SET = Set.of(
        "Rogue mask",
        "Rogue top",
        "Rogue trousers",
        "Rogue boots",
        "Rogue gloves"
    );

    private static final Map<String, WorldPoint[]> VYRE_HOUSES = Map.of(
        "Vallessia von Pitt", new WorldPoint[]{
            new WorldPoint(3661, 3378, 0),
            new WorldPoint(3664, 3378, 0),
            new WorldPoint(3664, 3376, 0),
            new WorldPoint(3667, 3376, 0),
            new WorldPoint(3667, 3381, 0),
            new WorldPoint(3661, 3382, 0)
        },
        "Misdrievus Shadum", new WorldPoint[]{
            new WorldPoint(3612, 3347, 0),
            new WorldPoint(3607, 3347, 0),
            new WorldPoint(3607, 3343, 0),
            new WorldPoint(3612, 3343, 0)
        },
        "Natalidae Shadum", new WorldPoint[]{
            new WorldPoint(3612, 3343, 0),
            new WorldPoint(3607, 3343, 0),
            new WorldPoint(3607, 3336, 0),
            new WorldPoint(3612, 3336, 0)
        }
        // add more...
    );

    @Inject
    public ThievingScript(final ThievingConfig config, final ThievingPlugin plugin)
    {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.isCantReachTargetDetectionEnabled = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (initialPlayerLocation == null) initialPlayerLocation = Rs2Player.getWorldLocation();
                switch(currentState) {
                    case IDLE:
                        if (!hasReqs()) {
                            currentState = State.BANK;
                            return;
                        }
                        currentState = State.PICKPOCKET;
                        break;
                    case BANK:
                        bankAndEquip();
                        currentState = State.IDLE;
                        break;
                    case PICKPOCKET:
                        if (Rs2Player.isStunned()) {
                            sleepUntil(() -> !Rs2Player.isStunned(), 8000);
                            return;
                        }
                        wearIfNot("dodgy necklace");

                        if (!autoEatAndDrop()) {
                            currentState = State.IDLE;
                            return;
                        }

                        switch (config.THIEVING_NPC()) {
                            case WEALTHY_CITIZEN:
                                pickpocketWealthyCitizen();
                                break;
                            case ELVES:
                                pickpocketElves();
                                break;
                            case VYRES:
                                pickpocketVyre();
                                break;
                            case ARDOUGNE_KNIGHT:
                                pickpocketArdougneKnight();
                                break;
                            default:
                                Rs2NpcModel npc = Rs2Npc.getNpc(config.THIEVING_NPC().getName());
                                pickpocketDefault(npc);
                                break;
                        }
                        break;
                    default:
                        // idk
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasReqs() {
        boolean hasFood = Rs2Inventory.getInventoryFood().size() >= config.foodAmount();
        boolean hasDodgy = Rs2Inventory.hasItem("Dodgy necklace") || config.dodgyNecklaceAmount() == 0;

        if (config.shadowVeil()) {
            boolean hasCosmic = Rs2Inventory.hasItem("Cosmic rune");
            boolean hasStaff = Rs2Equipment.isWearing("Lava battlestaff");
            boolean hasRunes = hasStaff || Rs2Inventory.hasItem("Earth rune", "Fire rune");
            return hasFood && hasDodgy && hasCosmic && hasRunes;
        }

        return hasFood && hasDodgy;
    }

    private boolean isPointInPolygon(WorldPoint[] polygon, WorldPoint point) {
        // thank'u duck
        int n = polygon.length;
        if (n < 3) return false;

        int plane = polygon[0].getPlane();
        if (point.getPlane() != plane) return false;

        boolean inside = false;
        int px = point.getX(), py = point.getY();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = polygon[i].getX(), yi = polygon[i].getY();
            int xj = polygon[j].getX(), yj = polygon[j].getY();
            boolean intersect = ((yi > py) != (yj > py)) && (px < (double)(xj - xi) * (py - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private boolean autoEatAndDrop() {
        if (config.useFood()) {
            if (Rs2Inventory.getInventoryFood().isEmpty()) {
                openCoinPouches();
                return false;
            }
            Rs2Player.eatAt(config.hitpoints());
        }

        if (Rs2Inventory.isFull()) {
            Rs2Player.eatAt(99);
            dropAllExceptImportant();
        }
        return true;
    }

    private void castShadowVeil() {
        if (!Rs2Magic.isShadowVeilActive() && Rs2Magic.canCast(MagicAction.SHADOW_VEIL)) {
            Rs2Magic.cast(MagicAction.SHADOW_VEIL);
            sleep(600);
        }
    }

    private void openCoinPouches() {
        int threshold = Math.max(1, Math.min(plugin.getMaxCoinPouch(), config.coinPouchTreshHold() + (int)(Math.random() * 7 - 3)));
        if (Rs2Inventory.hasItemAmount("coin pouch", threshold, true)) {
            Rs2Inventory.interact("coin pouch", "Open-all");
        }
    }

    private void wearIfNot(String item) {
        if (!Rs2Equipment.isWearing(item)) {
            Rs2Inventory.wield(item);
        }
    }

    private void pickpocketDefault(Rs2NpcModel npc) {
        if (!pickpocketHighlighted()) {
            if (npc == null) {
                Rs2Walker.walkTo(initialPlayerLocation, 0);
                Rs2Player.waitForWalking();
            } else {
                equipSet(ROGUE_SET);
                while (!Rs2Player.isStunned() & isRunning()) {
					if (!Microbot.isLoggedIn()) break;
                    openCoinPouches();
                    if (config.shadowVeil()) castShadowVeil();
                    if (!Rs2Npc.pickpocket(npc)) continue;
                    sleep(200, 300);
                }
            }
        }
    }

    private void pickpocketDefault(Set<String> targets) {
        Rs2NpcModel npc = Rs2Npc.getNpcs().filter(x -> targets.contains(x.getName())).findFirst().orElse(null);
        pickpocketDefault(npc);
    }

    private boolean pickpocketHighlighted() {
        var highlighted = net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
        if (highlighted.isEmpty()) return false;
        equipSet(ROGUE_SET);
        while (!Rs2Player.isStunned() & isRunning()) {
			if (!Microbot.isLoggedIn()) break;
            openCoinPouches();
            if (config.shadowVeil()) castShadowVeil();
            if (!Rs2Npc.pickpocket(highlighted)) continue;
            sleep(200, 300);
        }
        return true;
    }

    private void pickpocketElves() {
        Set<String> elfs = new HashSet<>(Arrays.asList(
            "Anaire","Aranwe","Aredhel","Caranthir","Celebrian","Celegorm","Cirdan","Curufin","Earwen","Edrahil",
            "Elenwe","Elladan","Enel","Erestor","Enerdhil","Enelye","Feanor","Findis","Finduilas","Fingolfin",
            "Fingon","Galathil","Gelmir","Glorfindel","Guilin","Hendor","Idril","Imin","Iminye","Indis","Ingwe",
            "Ingwion","Lenwe","Lindir","Maeglin","Mahtan","Miriel","Mithrellas","Nellas","Nerdanel","Nimloth",
            "Oropher","Orophin","Saeros","Salgant","Tatie","Thingol","Turgon","Vaire","Goreu"
        ));
        pickpocketDefault(elfs);
    }

    private void pickpocketVyre() {
        Set<String> vyres = new HashSet<>(Arrays.asList(
            "Natalidae Shadum", "Misdrievus Shadum", "Vallessia von Pitt" // add more...
        ));
        Rs2NpcModel vyre = Rs2Npc.getNpcs().filter(x -> vyres.contains(x.getName())).findFirst().orElse(null);
        if (vyre == null) {
            pickpocketDefault((Rs2NpcModel) null);
            return;
        }
        WorldPoint[] housePolygon = VYRE_HOUSES.get(vyre.getName());
        boolean npcInside = isPointInPolygon(housePolygon, vyre.getWorldLocation());
        boolean playerInside = isPointInPolygon(housePolygon, Rs2Player.getWorldLocation());

        if (!npcInside && playerInside) {
            boolean inside = waitUntilBothInPolygon(housePolygon, vyre, 8000 + (int)(Math.random() * 4000));
            if (!inside) {
                HopToWorld();
                return;
            }
        } else {
            closeNearbyDoor(3);
            pickpocketDefault(vyre);
        }
    }

    private void pickpocketArdougneKnight() {
        WorldArea ardougneArea = new WorldArea(2649, 3280, 7, 8, 0);
        Rs2NpcModel knight = Rs2Npc.getNpc("knight of ardougne");
        if (knight == null || config.ardougneAreaCheck() && !ardougneArea.contains(knight.getWorldLocation())) {
            Microbot.showMessage("Knight not in Ardougne area or not found. Shutting down");
            shutdown();
            return;
        }
        pickpocketDefault(knight);
    }

    private void pickpocketWealthyCitizen() {
        Rs2NpcModel npc = Rs2Npc.getNpcs("Wealthy citizen", true)
            .filter(x -> x != null && x.isInteracting() && x.getInteracting() != null)
            .findFirst().orElse(null);
        if (npc != null && !Rs2Player.isAnimating(3000)) {
            pickpocketDefault(npc);
        }
    }

    private void closeNearbyDoor(int radius) {
        Rs2GameObject.getAll(
            o -> {
                ObjectComposition comp = Rs2GameObject.convertToObjectComposition(o);
                return comp != null && Arrays.asList(comp.getActions()).contains("Close");
            },
            Rs2Player.getWorldLocation(),
            radius
        ).forEach(door -> {
            if (Rs2GameObject.interact(door, "Close")) {
                Rs2Player.waitForWalking();
            }
        });
    }

    private void equipSet(Set<String> set) {
        for (String item : set) {
            if (!Rs2Equipment.isWearing(item)) {
                if (Rs2Inventory.contains(item)) {
                    Rs2Inventory.wear(item);
                    Rs2Inventory.waitForInventoryChanges(3000);
                } else if (Rs2Bank.hasBankItem(item)) {
                    if (Rs2Player.getWorldLocation().getRegionID() == DARKMEYER_REGION) {
                        Rs2Bank.withdrawItem(item);
                    } else {
                        Rs2Bank.withdrawAndEquip(item);
                    }
                    Rs2Inventory.waitForInventoryChanges(3000);
                }
            }
        }
    }

    private void bankAndEquip() {
        BankLocation bank = Rs2Bank.getNearestBank();
        if (bank == BankLocation.DARKMEYER) equipSet(VYRE_SET);
        boolean opened = Rs2Bank.isNearBank(bank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(bank);
        if (!opened || !Rs2Bank.isOpen()) return;
        Rs2Bank.depositAll();

        boolean successfullyWithdrawFood = Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
        Rs2Inventory.waitForInventoryChanges(3000);

        if (!successfullyWithdrawFood) {
            Microbot.showMessage("No " + config.food().getName() + " found in bank.");
            shutdown();
            return;
        }

        boolean foodWasEat = false;
        if (config.eatFullHpBank()) {
            while (!Rs2Player.isFullHealth() && Rs2Player.useFood()) {
                Rs2Player.waitForAnimation();
                foodWasEat = true;
            }

            if (foodWasEat) {
                Set<String> keep = new HashSet<>();
                Rs2Inventory.getInventoryFood().forEach(food -> keep.add(food.getName()));
                Rs2Bank.depositAll(x -> !keep.contains(x.getName()));

                int foodActual = Rs2Inventory.getInventoryFood().size();
                int foodMiss = config.foodAmount() - foodActual;
                if (foodMiss > 0) {
                    Rs2Bank.withdrawX(false, config.food().getName(), foodMiss, true);
                    Rs2Inventory.waitForInventoryChanges(3000);
                }
            }
        }

        boolean successDodgy = Rs2Bank.withdrawDeficit("Dodgy necklace", config.dodgyNecklaceAmount());
        Rs2Inventory.waitForInventoryChanges(3000);

        if (!successDodgy) {
            Microbot.showMessage("No Dodgy necklace found in bank.");
            shutdown();
            return;
        }

        if (config.shadowVeil()) {
            List<String> runesShadowVeil = Arrays.asList("Earth rune", "Fire rune");
            boolean banklavaStaff = Rs2Equipment.isWearing("Lava battlestaff") || Rs2Inventory.contains("Lava battlestaff") || Rs2Bank.hasItem("Lava battlestaff");
            boolean bankrunes = Rs2Bank.hasItem(runesShadowVeil);
            boolean bankcosmicRune = Rs2Bank.hasItem("Cosmic rune");

            if (!banklavaStaff && !bankrunes) {
                Microbot.showMessage("No Lava battlestaff and runes (Earth, Fire) found in bank.");
                shutdown();
                return;
            }

            if (!bankcosmicRune) {
                Microbot.showMessage("No Cosmic rune found in bank.");
                shutdown();
                return;
            }

            if (banklavaStaff) {
                Rs2Bank.withdrawItem("Lava battlestaff");
                Rs2Inventory.waitForInventoryChanges(3000);
                if (Rs2Inventory.contains("Lava battlestaff")) {
                    Rs2Inventory.wear("Lava battlestaff");
                    Rs2Inventory.waitForInventoryChanges(3000);
                }
            } else {
                Rs2Bank.withdrawAll(true, "Fire rune", true);
                Rs2Inventory.waitForInventoryChanges(3000);
                Rs2Bank.withdrawAll(true, "Earth rune", true);
                Rs2Inventory.waitForInventoryChanges(3000);
            }
            Rs2Bank.withdrawAll(true, "Cosmic rune", true);
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        equipSet(ROGUE_SET);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    private void dropAllExceptImportant() {
        Set<String> keep = new HashSet<>();
        if (config.DoNotDropItemList() != null && !config.DoNotDropItemList().isEmpty())
            keep.addAll(Arrays.asList(config.DoNotDropItemList().split(",")));
        Rs2Inventory.getInventoryFood().forEach(food -> keep.add(food.getName()));
        keep.add("dodgy necklace"); keep.add("coins"); keep.add("coin pouch"); keep.add("book of the dead"); keep.add("drakan's medallion");
        if (config.shadowVeil()) Collections.addAll(keep, "Fire rune", "Earth rune", "Cosmic rune");
        keep.addAll(VYRE_SET); keep.addAll(ROGUE_SET);
        Rs2Inventory.dropAllExcept(config.keepItemsAboveValue(), keep.toArray(new String[0]));
    }

    private boolean waitUntilBothInPolygon(WorldPoint[] polygon, Rs2NpcModel npc, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!Microbot.isLoggedIn()) return false;
            boolean npcInside = isPointInPolygon(polygon, npc.getWorldLocation());
            boolean playerInside = isPointInPolygon(polygon, Rs2Player.getWorldLocation());
            if (npcInside && playerInside) {
                return true;
            }
            sleep(250, 350);
        }
        return false;
    }

    private void HopToWorld() {
        int attempts = 0;
        int maxtries = 5;
        Microbot.log("Hopping world, please wait...");
        while (attempts < maxtries) {
            int world = Login.getRandomWorld(true, null);
            Microbot.hopToWorld(world);
            boolean hopSuccess = sleepUntil(() -> Rs2Player.getWorld() == world, 10000);
            if (hopSuccess) break;
            sleep(250, 350);
            attempts++;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Microbot.isCantReachTargetDetectionEnabled = false;
    }
}
