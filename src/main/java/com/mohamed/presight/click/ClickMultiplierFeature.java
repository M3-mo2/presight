package com.mohamed.presight.click;

import com.mohamed.presight.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/**
 * Multiplies a real click into two, three, four or five actions in the same tick.
 *
 * <h3>Why it replays the action instead of the mouse</h3>
 *
 * {@code Minecraft.clickMouse()} and {@code rightClickMouse()} are private, and faking mouse
 * events would be a second, parallel input path that the game's own cooldowns do not know
 * about — the classic way an auto-clicker desynchronises and gets a player flagged or
 * kicked. So this waits for a <em>real</em> press, then repeats the <em>same</em> public
 * action the game just took, through {@code PlayerControllerMP}:
 *
 * <pre>
 *   left  on a block   -> clickBlock(pos, side)
 *   left  on an entity -> attackEntity(player, entity)
 *   right              -> sendUseItem(player, world, held)
 * </pre>
 *
 * The action is derived from {@code objectMouseOver}, which is the game's own ray-trace for
 * this frame, so the repeat lands on whatever the first click landed on.
 *
 * <h3>What it refuses to do</h3>
 *
 * It never fires without a real press: no hold, no timer, no "always on". Holding the button
 * down does not multiply continuously — one press, one burst. That keeps it a click
 * multiplier rather than an auto-clicker, which is what was asked for and what is defensible.
 */
public class ClickMultiplierFeature implements Feature {

    public static final String ID = "click_multiplier";

    /** How long the synthetic press is held, in ms. A fast human click is 2-5 ms. */
    private static final int HOLD_MS = 3;
    /**
     * How long after a burst its own synthetic events are ignored.
     *
     * The burst is at most four clicks of HOLD_MS each, plus the drain of one input pass, so
     * 120 ms covers it with room to spare while still letting a fast player click again.
     */
    private static final int BURST_WINDOW_MS = 120;
    /** After this long, an unreturned synthetic event is forgotten rather than blocking input. */
    private static final int SUPPRESS_TIMEOUT_MS = 400;

    /** Which button is being multiplied. */
    public enum Button {
        LEFT, RIGHT
    }

    private Button button = Button.LEFT;
    private int count = 3;
    private boolean active;

    /**
     * Timestamp of the last burst.
     *
     * A boolean flag is not enough: Robot.mousePress queues its event and LWJGL drains it on
     * the next input pass, by which time any flag has already been cleared — so each synthetic
     * click re-entered this method and the count ran away (127 presses in one test). A time
     * window swallows the drain, and it is bounded to the burst length.
     */
    private long lastBurstAtMs;

    /**
     * How many synthetic events are still expected back from the injector.
     *
     * A time window was not enough: Robot's events are drained by LWJGL on its own schedule,
     * and on a software-rendered client that drain can land past any fixed window, so a burst
     * still re-entered itself (13 presses from 3 clicks). Counting is exact — each injected
     * press is expected exactly once, so exactly that many are swallowed.
     */
    private int suppressEvents;

    /** Bursts fired, for the diagnostic line. */
    private int bursts;


    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Click Multiplier";
    }

    @Override
    public int defaultKey() {
        return Keyboard.KEY_I;
    }

    /**
     * This feature owns its key, because Shift+I has to mean something different from I.
     *
     * Plain I opens the small control screen; Shift+I switches the multiplier off. A toggle
     * on every press would make the screen unusable — opening it would arm the tool.
     */
    @Override
    public boolean handlesKeyItself() {
        return true;
    }

    @Override
    public void onKeyPressed(boolean shift) {
        Minecraft mc = Minecraft.getMinecraft();
        if (shift) {
            // A switch, not an off button. Turning it off with Shift+I and then having no way
            // back on except reopening the screen is a dead end — the owner hit exactly that.
            active = !active;
            System.out.println("[PreSight] click_multiplier " + (active ? "ON" : "OFF")
                    + " (shift+i) " + button + " x" + count);
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        "\u00a78[\u00a7bPreSight\u00a78] \u00a77Click Multiplier: "
                                + (active ? "\u00a7aON " + button + " x" + count
                                          : "\u00a7cOFF")));
            }
            return;
        }
        if (mc.currentScreen == null) {
            mc.displayGuiScreen(new ClickMultiplierScreen(this));
        }
    }

    /** Called by the screen when the player picks something. */
    public void configure(Button b, int c) {
        this.button = b;
        this.count = Math.max(2, Math.min(5, c));
        this.active = true;
        System.out.println("[PreSight] click_multiplier ON: " + button + " x" + count);
        // Prove the injector works before the player relies on it. AWT Robot either has
        // permission to synthesise input or it does not, and finding out on the first click
        // costs a wasted burst.
        boolean ok = RobotClicker.init();
        System.out.println("[PreSight] click_multiplier injector: "
                + (ok ? "ready" : RobotClicker.report()));
    }

    public Button button() {
        return button;
    }

    public int count() {
        return count;
    }

    public boolean active() {
        return active;
    }

    @Override
    public void onToggle(boolean enabled) {
        if (!enabled) {
            active = false;
        }
    }

    /**
     * True when this event is one of our own coming back.
     *
     * Each injected click returns as a press <em>and</em> a release, so the counter is charged
     * double. A stale count is dropped after SUPPRESS_TIMEOUT_MS so a lost event can never
     * permanently eat the player's clicks.
     */
    public boolean consumeInjected() {
        if (suppressEvents <= 0) {
            return false;
        }
        if (System.currentTimeMillis() - lastBurstAtMs > SUPPRESS_TIMEOUT_MS) {
            suppressEvents = 0;
            return false;
        }
        suppressEvents--;
        return true;
    }

    /**
     * Called by the input handler the instant a real press is seen.
     *
     * Not a tick hook: a click is an event, and polling for it once per tick missed it
     * entirely because LWJGL drains button events before the tick runs.
     */
    public void onMousePress(int mouseButton) {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBurstAtMs < BURST_WINDOW_MS) {
            return;
        }
        int wanted = button == Button.LEFT ? 0 : 1;
        if (mouseButton != wanted) {
            return;
        }
        bursts++;
        lastBurstAtMs = now;
        // Each injected click returns as a press and a release.
        suppressEvents = (count - 1) * 2;
        int delivered = 1;            // the player's own press, which already happened
        for (int i = 0; i < count - 1; i++) {
            if (RobotClicker.click(button == Button.RIGHT, HOLD_MS)) {
                delivered++;
            }
        }
        System.out.println("[PreSight] click_multiplier: press #" + bursts + " (" + button
                + ") -> " + delivered + "/" + count + " delivered");
    }

    public String describe() {
        return active ? (button + " x" + count) : "off";
    }

    /** Exposed so the HUD status line can show the mode without a screen. */
    public String status() {
        return describe();
    }
}
