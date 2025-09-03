package net.runelite.client.plugins.microbot.kee.greendragons;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Green Dragons",
        description = "Green dragon farmer for bones",
        tags = {"blue", "dragons", "prayer"},
        enabledByDefault = false
)
public class GreenDragonsPlugin extends Plugin {
    static final String CONFIG = "greendragons";

    @Inject
    private GreenDragonsScript script;

    @Inject
    private GreenDragonsConfig config;
    
    @Inject
    private GreenDragonsOverlay overlay;
    
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Client client;

    @Override
    protected void startUp() {
        overlay.setScript(script);
        
        overlay.setConfig(config);
        
        overlayManager.add(overlay);

        Rs2Antiban.activateAntiban();

        Rs2Antiban.resetAntibanSettings();

        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();

        Rs2Antiban.setActivity(Activity.KILLING_BLUE_DRAGONS);
        
        if (config.startPlugin()) {
            script.run(config);
        }
    }

    @Override
    protected void shutDown() {
        script.logOnceToChat("Stopping Blue Dragons plugin...", false, config);
        overlayManager.remove(overlay);
        script.shutdown();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("greendragons")) return;

        switch (event.getKey()) {
            case "startPlugin":
                if (config.startPlugin()) {
                    script.logOnceToChat("Starting Green Dragon plugin...", false, config);
                    script.run(config);
                } else {
                    script.logOnceToChat("Stopping Green Dragon plugin!", false, config);
                    script.shutdown();
                }
                break;

            case "lootDragonhide":
            case "foodType":
            case "foodAmount":
            case "eatAtHealthPercent":
            case "lootEnsouledHead":
            case "debugLogs":
                script.logOnceToChat("Configuration changed. Updating script settings.", true, config);
                if (config.startPlugin()) {
                    script.updateConfig(config);
                }
                break;

            default:
                break;
        }
    }


    @Provides
    GreenDragonsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GreenDragonsConfig.class);
    }
}
