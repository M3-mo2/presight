package com.mohamed.presight.click;

import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * Clicks the mouse for real: an OS-level press and release, not a synthetic game action.
 *
 * <h3>Why this and not the controller</h3>
 *
 * Replaying {@code PlayerControllerMP} calls tried to be clever and produced nothing the
 * player could see — no cooldown clearing helped, because the game had already decided this
 * tick's click was over. The owner's instruction was blunt and correct: make it click the
 * mouse, like a second press.
 *
 * {@link Robot} injects into the platform input queue, so the click arrives through exactly
 * the path a physical one takes — the same listener, the same cooldowns, the same code. If a
 * real double-click would register, this registers.
 *
 * <h3>The honest caveat</h3>
 *
 * It does not move the pointer. The repeats land wherever the player is aiming, which is
 * where the original click landed, so aim is preserved without touching the camera.
 *
 * AWT is initialised on first use; in a Minecraft client that is already true because the game
 * uses AWT for its own dialogs. If the platform refuses (headless, or no permission), the
 * caller is told and can fall back.
 */
public final class RobotClicker {

    private static Robot robot;
    private static boolean failed;
    private static String report = "not initialised";

    private RobotClicker() {
    }

    /** Prepare the Robot once; returns false when this platform will not allow it. */
    public static boolean init() {
        if (robot != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                failed = true;
                report = "headless — cannot click";
                return false;
            }
            robot = new Robot();
            robot.setAutoDelay(0);
            robot.setAutoWaitForIdle(false);
            report = "ready";
            return true;
        } catch (Throwable t) {
            failed = true;
            report = "failed: " + t;
            return false;
        }
    }

    public static String report() {
        return report;
    }

    /**
     * One complete click: press then release, with the button held for {@code holdMs}.
     *
     * A short hold matters. A press and release in the same millisecond is ignored by some
     * input stacks; two to five milliseconds is what a fast human click looks like.
     */
    public static boolean click(boolean rightButton, int holdMs) {
        if (!init()) {
            return false;
        }
        try {
            int mask = rightButton ? InputEvent.BUTTON3_DOWN_MASK
                                   : InputEvent.BUTTON1_DOWN_MASK;
            robot.mousePress(mask);
            if (holdMs > 0) {
                Thread.sleep(holdMs);
            }
            robot.mouseRelease(mask);
            return true;
        } catch (Throwable t) {
            System.err.println("[PreSight] robot click failed: " + t);
            return false;
        }
    }
}
