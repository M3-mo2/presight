package com.mohamed.presight.config;

import com.mohamed.presight.PreSight;
import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Remembers which features are on and what key each one uses.
 *
 * Deliberately NOT Forge's Configuration class, and deliberately not JSON or YAML:
 * every one of those readers decodes text and throws MalformedInputException when a
 * byte does not fit the expected encoding. Raised during mod loading, that exception
 * takes the whole game down before the player reaches a world — for a file that
 * stores two booleans and two key names.
 *
 * So: java.util.Properties, read and written with an explicit UTF-8 charset, and
 * every path tolerates failure. A corrupt or unreadable config means "use the
 * defaults", never "crash on startup".
 */
@SideOnly(Side.CLIENT)
public final class ConfigStore {

    private static final String FILE_NAME = "presight.properties";
    private static File file;
    private static boolean loaded;

    private ConfigStore() {
    }

    /** Called after the features are registered. Never throws. */
    public static void load() {
        loaded = true;
        try {
            PreSight mod = PreSight.get();
            File dir = mod == null ? new File("config") : mod.configDirectory();
            if (dir == null) {
                return;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            file = new File(dir, FILE_NAME);

            Properties props = readAll();

            for (Feature f : FeatureRegistry.all()) {
                String on = props.getProperty("enabled." + f.id());
                if (on != null) {
                    FeatureRegistry.setEnabled(f.id(), Boolean.parseBoolean(on.trim()));
                }
                String key = props.getProperty("key." + f.id());
                if (key != null && !key.trim().isEmpty()) {
                    int code = Keyboard.getKeyIndex(key.trim());
                    if (code != Keyboard.KEY_NONE) {
                        com.mohamed.presight.keybind.Keybinds.rebind(f.id(), code);
                    }
                }
            }
        } catch (Throwable t) {
            // Reading a config file is never worth a crash.
            System.err.println("[PreSight] config skipped, defaults in use: " + t);
        }
    }

    /** Persist one feature's state. Called on every toggle, so a crash keeps it. */
    public static void saveEnabled(String id, boolean enabled) {
        try {
            Properties props = readAll();
            props.setProperty("enabled." + id, Boolean.toString(enabled));
            write(props);
        } catch (Throwable t) {
            System.err.println("[PreSight] could not save config: " + t);
        }
    }

    private static Properties readAll() {
        Properties props = new Properties();
        if (file == null || !file.isFile() || file.length() == 0) {
            return props;
        }
        try (Reader r = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException | RuntimeException e) {
            // Unreadable file: start clean rather than propagate. The next save
            // rewrites it, which also repairs whatever encoding damage was there.
            System.err.println("[PreSight] config unreadable, rewriting: " + e);
            props.clear();
        }
        return props;
    }

    private static void write(Properties props) {
        if (file == null) {
            return;
        }
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            props.store(w, "PreSight feature states. ASCII only on purpose.");
        } catch (IOException | RuntimeException e) {
            System.err.println("[PreSight] could not write config: " + e);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
