package com.mohamed.presight.keybind;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One keybinding per registered feature, created from the registry.
 *
 * Because the bindings are generated from the feature list, adding a feature adds a
 * rebindable key with no changes here. Keys are also registered in Minecraft's own
 * controls screen, so the user can rebind without editing a config file.
 */
@SideOnly(Side.CLIENT)
public final class Keybinds {

    public static final String CATEGORY = "key.presight.category";

    private static final List<KeyBinding> BINDINGS = new ArrayList<>();
    private static final Map<KeyBinding, Feature> OWNER = new HashMap<>();

    public Keybinds() {
    }

    /** Called once, after the features are registered. */
    public static void registerAll() {
        BINDINGS.clear();
        OWNER.clear();
        for (Feature f : FeatureRegistry.all()) {
            KeyBinding kb = new KeyBinding("key.presight." + f.id(), f.defaultKey(), CATEGORY);
            ClientRegistry.registerKeyBinding(kb);
            BINDINGS.add(kb);
            OWNER.put(kb, f);
        }
    }

    /** True while the player is typing — a toggle key must not fire inside chat. */
    private static boolean typing() {
        return Minecraft.getMinecraft().currentScreen instanceof GuiChat;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (typing()) {
            return;
        }
        // Consume every press in one pass so two features bound to the same key both
        // see it rather than one swallowing the other.
        for (Map.Entry<KeyBinding, Feature> e : OWNER.entrySet()) {
            if (e.getKey().isPressed()) {
                Feature f = e.getValue();
                boolean on = FeatureRegistry.toggle(f.id());
                com.mohamed.presight.config.ConfigStore.saveEnabled(f.id(), on);
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                                "\u00a78[\u00a7bPreSight\u00a78] \u00a77" + f.displayName()
                                        + ": " + (on ? "\u00a7aON" : "\u00a7cOFF")));
            }
        }
    }

    /** Exposed for the settings screen later. */
    public static List<KeyBinding> all() {
        return BINDINGS;
    }

    /** Apply a stored key to a binding, looked up by feature id. */
    public static void rebind(String featureId, int keyCode) {
        for (Map.Entry<KeyBinding, Feature> e : OWNER.entrySet()) {
            if (e.getValue().id().equals(featureId)) {
                e.getKey().setKeyCode(keyCode);
                return;
            }
        }
    }

    /** The default key of a feature as a readable name, e.g. "G". */
    public static String keyName(Feature f) {
        return Keyboard.getKeyName(f.defaultKey());
    }
}
