package com.mohamed.presight.config;

import com.mohamed.presight.PreSight;
import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists which features are on, and their keys.
 *
 * Without this, every restart forgets what the player turned on, which reads as the
 * mod being broken. Keys are stored by name ("G") rather than by LWJGL code so the
 * file stays readable and survives a keyboard layout change.
 */
@SideOnly(Side.CLIENT)
public final class ConfigStore {

    private static final Map<String, Integer> KEYS = new HashMap<>();
    private static Configuration cfg;

    private ConfigStore() {
    }

    /** Called after the features are registered. */
    public static void load() {
        PreSight mod = PreSight.get();
        if (mod == null) {
            return;
        }
        cfg = mod.openConfig();
        cfg.setCategoryComment("features", "Toggle each feature; they can also be toggled in game.");

        for (Feature f : FeatureRegistry.all()) {
            boolean on = cfg.getBoolean(f.id(), "features", f.defaultEnabled(),
                    f.displayName());
            FeatureRegistry.setEnabled(f.id(), on);

            String stored = cfg.getString(f.id(), "keys",
                    Keyboard.getKeyName(f.defaultKey()), "Key for " + f.displayName());
            int code = Keyboard.getKeyIndex(stored);
            if (code != Keyboard.KEY_NONE) {
                com.mohamed.presight.keybind.Keybinds.rebind(f.id(), code);
            }
        }

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    /** Persist one feature's state. Called on every toggle so a crash keeps it. */
    public static void saveEnabled(String id, boolean enabled) {
        if (cfg == null) {
            return;
        }
        cfg.get("features", id, enabled).set(enabled);
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static int keyFor(String id, int fallback) {
        Integer k = KEYS.get(id);
        return k == null ? fallback : k;
    }
}
