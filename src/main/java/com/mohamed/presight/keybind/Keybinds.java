package com.mohamed.presight.keybind;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the toggle keys straight from the physical keyboard.
 *
 * The KeyBinding event path was used first and it silently did nothing: a binding
 * only reports a press if Minecraft successfully registered it into GameSettings
 * while the client's settings already existed. When that ordering is off the binding
 * exists, shows up in Options -> Controls, and never fires — the key looks dead and
 * nothing is logged anywhere.
 *
 * So the keys are polled every client tick with edge detection: act when the physical
 * key is down now and was not down on the previous tick. KeyBinding is still created
 * and registered, because that is what puts the entries in the Controls screen and
 * makes them rebindable — but the press is read from LWJGL directly, which cannot
 * fail this way.
 *
 * The code honoured is whatever the binding's *current* code is, so a rebind done in
 * the options screen takes effect immediately.
 */
@SideOnly(Side.CLIENT)
public final class Keybinds {

    public static final String CATEGORY = "key.presight.category";

    private static final List<KeyBinding> BINDINGS = new ArrayList<>();
    private static final Map<KeyBinding, Feature> OWNER = new LinkedHashMap<>();

    /**
     * The optional second binding per feature: the key that only ever switches it.
     *
     * Kept in its own map so the main loop cannot double-fire when a feature has both a
     * control key and a switch key, and so a feature without one costs nothing.
     */
    private static final Map<KeyBinding, Feature> TOGGLE_OWNER = new LinkedHashMap<>();

    /** Keys physically down on the previous tick, for edge detection. */
    private static final Set<Integer> HELD_LAST_TICK = new HashSet<>();

    public Keybinds() {
    }

    /** Called once, after the features are registered. */
    public static void registerAll() {
        BINDINGS.clear();
        OWNER.clear();
        for (Feature f : FeatureRegistry.all()) {
            KeyBinding kb = new KeyBinding("key.presight." + f.id(), f.defaultKey(), CATEGORY);
            try {
                ClientRegistry.registerKeyBinding(kb);
            } catch (Throwable t) {
                // Having the key in the Controls list is a nicety; polling still works
                // without it, so never let this take the mod down.
                System.err.println("[PreSight] could not register a Controls entry for "
                        + f.id() + ": " + t);
            }
            BINDINGS.add(kb);
            OWNER.put(kb, f);

            if (f.toggleKey() > 0) {
                KeyBinding tkb = new KeyBinding("key.presight." + f.id() + ".toggle",
                        f.toggleKey(), CATEGORY);
                try {
                    ClientRegistry.registerKeyBinding(tkb);
                } catch (Throwable t) {
                    System.err.println("[PreSight] could not register the switch key for "
                            + f.id() + ": " + t);
                }
                BINDINGS.add(tkb);
                TOGGLE_OWNER.put(tkb, f);
            }
        }

        for (KeyBinding kb : BINDINGS) {
            System.out.println("[PreSight] bound " + kb.getKeyDescription() + " -> "
                    + Keyboard.getKeyName(kb.getKeyCode()));
        }
    }

    /**
     * Called every client tick. This is the only place that acts on a key.
     *
     * Any open screen — chat, inventory, pause — clears the held state and skips, so a
     * toggle can never fire while the player is typing a message.
     */
    public static void poll() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) {
            HELD_LAST_TICK.clear();
            return;
        }

        Set<Integer> heldNow = new HashSet<>();
        for (Map.Entry<KeyBinding, Feature> e : OWNER.entrySet()) {
            int code = e.getKey().getKeyCode();
            if (code <= 0) {
                continue;
            }
            if (Keyboard.isKeyDown(code)) {
                heldNow.add(code);
                if (!HELD_LAST_TICK.contains(code)) {
                    Feature f = e.getValue();
                    if (f.handlesKeyItself()) {
                        // Let the feature see Shift: one key, two meanings, and the decision
                        // stays with the feature that knows what the two meanings are.
                        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        try {
                            f.onKeyPressed(shift);
                        } catch (Throwable t) {
                            System.err.println("[PreSight] key '" + f.id() + "' threw: " + t);
                        }
                    } else {
                        toggle(f);
                    }
                }
            }
        }
        // The switch keys. Same edge detection against the same set, so one press is one
        // switch no matter how many bindings point at the key.
        for (Map.Entry<KeyBinding, Feature> e : TOGGLE_OWNER.entrySet()) {
            int code = e.getKey().getKeyCode();
            if (code <= 0 || !Keyboard.isKeyDown(code)) {
                continue;
            }
            heldNow.add(code);
            if (!HELD_LAST_TICK.contains(code)) {
                try {
                    e.getValue().onTogglePressed();
                } catch (Throwable t) {
                    System.err.println("[PreSight] switch key for '" + e.getValue().id()
                            + "' threw: " + t);
                }
            }
        }

        HELD_LAST_TICK.clear();
        HELD_LAST_TICK.addAll(heldNow);
    }

    private static void toggle(Feature f) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean on = FeatureRegistry.toggle(f.id());
        com.mohamed.presight.config.ConfigStore.saveEnabled(f.id(), on);

        // Logged as well as shown: if the chat line is missed, the log still says what
        // happened, which is the difference between a ten-second check and a hunt.
        System.out.println("[PreSight] " + f.id() + " -> " + (on ? "ON" : "OFF"));
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "\u00a78[\u00a7bPreSight\u00a78] \u00a77" + f.displayName()
                            + ": " + (on ? "\u00a7aON" : "\u00a7cOFF")));
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

    /** What the log should show once the bindings exist. */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (KeyBinding kb : BINDINGS) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(kb.getKeyDescription()).append('=')
                    .append(Keyboard.getKeyName(kb.getKeyCode()));
        }
        return sb.toString();
    }
}
