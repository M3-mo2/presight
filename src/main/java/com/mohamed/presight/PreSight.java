package com.mohamed.presight;

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
 *
 * Every entry point here is defensive. An exception escaping a mod's preInit during
 * mod loading is fatal to the whole game, so nothing in this path is allowed to throw:
 * a broken config or an unavailable client class must degrade, not crash.
 */
@Mod(modid = PreSight.MODID, name = PreSight.NAME, version = PreSight.VERSION,
     acceptableRemoteVersions = "*", clientSideOnly = true)
public class PreSight {

    public static final String MODID = "presight";
    public static final String NAME = "PreSight";
    public static final String VERSION = "1.10.1";

    @Mod.Instance(MODID)
    public static PreSight instance;

    private File configDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            configDir = event.getModConfigurationDirectory();
        } catch (Throwable t) {
            System.err.println("[PreSight] no config directory: " + t);
        }
        // Client-only data, but preInit still runs on both sides in a dev workspace.
        if (event.getSide() == Side.CLIENT) {
            try {
                com.mohamed.presight.client.PreSightClient.init();
            } catch (Throwable t) {
                // A failure here must still leave a loadable mod: the game starts,
                // the features are simply off, and the log says why.
                System.err.println("[PreSight] client init failed, features disabled: " + t);
                t.printStackTrace();
            }
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            try {
                com.mohamed.presight.client.PreSightClient.registerHandlers();
            } catch (Throwable t) {
                System.err.println("[PreSight] handler registration failed: " + t);
                t.printStackTrace();
            }
        }
    }

    /** The directory config files live in; may be null if discovery failed. */
    public File configDirectory() {
        return configDir;
    }

    public static PreSight get() {
        return instance;
    }
}
