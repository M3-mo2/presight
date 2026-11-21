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
     * True when this feature decides what its own key does, instead of being toggled by it.
     *
     * Needed because Shift+I must mean something different from I. The alternative — a
     * toggle on every press — makes the control screen unusable, since opening it would arm
     * the tool.
     */
    default boolean handlesKeyItself() {
        return false;
    }

    /**
     * An optional second key that always switches this feature on and off.
     *
     * Exists because one key cannot carry three meanings. The multiplier needs I to open its
     * control screen and Shift+I to toggle, and the owner wants a plain, dedicated switch as
     * well — reaching for Shift while fighting is not a control scheme. Returning 0 means the
     * feature has no separate switch. The key is registered like any other, so it appears in
     * Options -> Controls and can be rebound.
     */
    default int toggleKey() {
        return 0;
    }

    /**
     * Called on a press of {@link #toggleKey()}. Never called when that key is 0.
     *
     * Separate from {@link #onKeyPressed(boolean)} because this press has exactly one meaning:
     * switch. A feature whose enabled state lives in the registry gets that for free through
     * {@link com.mohamed.presight.feature.FeatureRegistry#toggle(String)}; one that keeps its own
     * state overrides this.
     */
    default void onTogglePressed() {
    }

    /**
     * Called on a press of this feature's key, only when {@link #handlesKeyItself()}.
     *
     * @param shift whether either Shift was held, so one key can carry two meanings.
     */
    default void onKeyPressed(boolean shift) {
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
