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

    public static void setEnabled(String id, boolean enabled) {
        if (!BY_ID.containsKey(id)) {
            return;
        }
        ENABLED.put(id, enabled);
        BY_ID.get(id).onToggle(enabled);
    }
}
