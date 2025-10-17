package net.runelite.client.plugins.microbot.kee.darkcrabs;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "[Kee] Dark Crab Fisher",
        description = "",
        tags = {"Kee"},
        enabledByDefault = false
)
@Slf4j
public class DarkCrabsPlugin extends Plugin {
    @Inject
    private DarkCrabsConfig config;
    @Provides
    DarkCrabsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DarkCrabsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private DarkCrabsOverlay darkCrabsOverlay;

    @Inject
    DarkCrabsScript darkCrabsScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(darkCrabsOverlay);
        }
        darkCrabsScript.run(config);
        DarkCrabsInfo.botStatus = config.STARTING_STATE();
    }

    protected void shutDown() {
        darkCrabsScript.shutdown();
        overlayManager.remove(darkCrabsOverlay);
    }
}
