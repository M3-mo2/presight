package com.mohamed.presight.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every feature, in one place.
 *
 * Adding an ability is: write a Feature, add one line here. The keybinding, the
 * config entry and both render passes iterate this list, so nothing else has to
 * know the new feature exists.
 */
public final class FeatureRegistry {

    private static final Map<String, Feature> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();

    private FeatureRegistry() {
    }

    public static void register(Feature feature) {
        BY_ID.put(feature.id(), feature);
        ENABLED.put(feature.id(), feature.defaultEnabled());
    }

    public static List<Feature> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static Feature byId(String id) {
        return BY_ID.get(id);
    }

    public static boolean isEnabled(String id) {
        Boolean b = ENABLED.get(id);
        return b != null && b;
    }

    /**
     * Flip a feature and tell it.
     *
     * @return the new state, or false when the id is unknown.
     */
    public static boolean toggle(String id) {
        if (!BY_ID.containsKey(id)) {
            return false;
        }
        boolean next = !ENABLED.get(id);
        ENABLED.put(id, next);
        BY_ID.get(id).onToggle(next);
        return next;
    }

    /**
     * Tick every enabled feature once.
     *
     * One loop here rather than a listener per feature: Forge resolves handlers by
     * reflection, and two features registered on the wrong bus both look like "the feature
     * does nothing". This cannot be registered on the wrong bus, because it happens inside
     * the one handler that already works.
     */
    public static void tickAll(com.mohamed.presight.core.TickContext ctx) {
        for (Feature f : BY_ID.values()) {
            // Every feature is ticked; each decides for itself whether it has work.
            //
            // Not gated on ENABLED: a feature that owns its own key is never toggled through
            // the registry — the I screen configures without arming — so gating here meant its
            // tick never ran and a queued burst was never delivered.
            try {
                f.tick(ctx);
            } catch (Throwable t) {
                // One broken tool must not stop the others, and must not kill the game loop.
                System.err.println("[PreSight] tick '" + f.id() + "' threw:");
                t.printStackTrace();
            }
        }
    }

    public static void setEnabled(String id, boolean enabled) {
        if (!BY_ID.containsKey(id)) {
            return;
        }
        ENABLED.put(id, enabled);
        BY_ID.get(id).onToggle(enabled);
    }
}
