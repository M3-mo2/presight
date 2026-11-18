package com.mohamed.presight.feature;

/**
 * One toggleable ability.
 *
 * This exists so that adding the third, fourth and tenth feature does not mean
 * touching the mod class, the keybinding list, the config file and the renderer
 * every time. A feature owns its own state, its own key, and its own draw call; the
 * registry wires them together.
 *
 * Client-side only.
 */
public interface Feature {

    /** Stable id: used for the config key and the keybinding name. Never change it. */
    String id();

    /** Shown in the config and the chat confirmation. */
    String displayName();

    /** Default key code (LWJGL: Keyboard.KEY_G etc). The user can rebind in-game. */
    int defaultKey();

    /** Keys that must never be taken by default, e.g. movement. Checked when binding. */
    default boolean defaultEnabled() {
        return false;
    }

    /** Called when the feature is switched on or off. */
    default void onToggle(boolean enabled) {
    }

    // ---- rendering hooks, all optional ----------------------------------

    /** Draw in world space, after the world, before the HUD. */
    default void renderWorld(com.mohamed.presight.render.RenderContext ctx) {
    }

    /** Draw on the HUD, in screen coordinates. */
    default void renderScreen(com.mohamed.presight.render.ScreenContext ctx) {
    }
}
