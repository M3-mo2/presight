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

    /**
     * Called once per client tick, while the feature is enabled.
     *
     * Not a render hook: this is where a feature reacts to state that changes between
     * frames — footing, sneaking, a key held down — rather than drawing. Rendering runs at
     * frame rate and would run the same decision sixty times a second.
     */
    default void tick(com.mohamed.presight.core.TickContext ctx) {
    }

    // ---- rendering hooks, all optional ----------------------------------

    /** Draw in world space, after the world, before the HUD. */
    default void renderWorld(com.mohamed.presight.render.RenderContext ctx) {
    }

    /**
     * World-space measuring that must not run while the line batch is open.
     *
     * 1282 GL_INVALID_OPERATION means a state call was made between glBegin and glEnd,
     * and `glGetFloat` is a state call: reading the modelview/projection matrices to
     * project a world point onto the screen is illegal inside a primitive. Doing it in
     * `renderWorld` produced that error on every frame for every player.
     *
     * So the batch closes first, this runs with the world matrices still current, and
     * only then does the HUD pass draw using what this computed.
     */
    default void measureWorld(com.mohamed.presight.render.RenderContext ctx) {
    }

    /** Draw on the HUD, in screen coordinates. */
    default void renderScreen(com.mohamed.presight.render.ScreenContext ctx) {
    }
}
