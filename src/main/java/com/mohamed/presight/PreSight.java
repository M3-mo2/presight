package com.mohamed.presight;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;

/**
 * PreSight — see players and their gear through walls.
 *
 * Two features, one architecture:
 *   G   player ESP: a bright box around every player, at any distance
 *   Y   gear view: what a player is holding and wearing, next to their box
 *
 * Everything is driven through {@link com.mohamed.presight.feature.FeatureRegistry},
 * so adding a third ability later means writing a Feature and registering it — the
 * keybinding, the config entry and the render pass come for free.
 */
@Mod(modid = PreSight.MODID, name = PreSight.NAME, version = PreSight.VERSION,
     acceptableRemoteVersions = "*", clientSideOnly = true)
public class PreSight {

    public static final String MODID = "presight";
    public static final String NAME = "PreSight";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static PreSight instance;

    private File configFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = new File(event.getModConfigurationDirectory(), "presight.cfg");
        // Client-only data, but preInit still runs on both sides in a dev workspace.
        if (event.getSide() == Side.CLIENT) {
            com.mohamed.presight.client.PreSightClient.init();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            com.mohamed.presight.client.PreSightClient.registerHandlers();
        }
    }

    /** Loaded on demand by the settings screen and the feature registry. */
    public Configuration openConfig() {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();
        } catch (Exception e) {
            System.err.println("[PreSight] config load failed, defaults in use: " + e);
        }
        return cfg;
    }

    public static PreSight get() {
        return instance;
    }
}
