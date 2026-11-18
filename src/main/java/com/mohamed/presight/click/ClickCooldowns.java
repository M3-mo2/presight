package com.mohamed.presight.click;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

/**
 * Clears the cooldowns that stop a second click landing, so a burst is not a slow burst.
 *
 * <h3>What actually limits click speed in 1.8</h3>
 *
 * Two private counters in {@link Minecraft}, plus one in the controller:
 *
 * <pre>
 *   Minecraft.leftClickCounter     ticks to wait before the next attack is registered
 *   Minecraft.rightClickDelayTimer ticks between item uses
 *   controller.isHittingBlock      set by clickBlock; a second click on the same block while
 *                                  it is set is ignored outright
 * </pre>
 *
 * They are private with no setter, so they are cleared by reflection once, and the lookups
 * are cached — a reflective write per repeat would cost more than the click it is enabling.
 *
 * <h3>What it deliberately does not do</h3>
 *
 * It does not send anything to the server by itself. Every action still goes through
 * {@code PlayerControllerMP}, the same code path a real click takes, which is why the server
 * sees genuine clicks rather than a fabricated packet stream. Clearing a cooldown makes the
 * <em>client</em> willing to click again immediately; it cannot make the server believe
 * something that did not happen.
 *
 * Reflection is used because 1.8 has no mixin and no access widener, and the alternative —
 * hooking the click methods — is not possible from a Forge mod without bytecode patching of
 * a vanilla class. If a lookup fails the field is simply skipped: the multiplier still works,
 * it just falls back to the game's own pacing instead of crashing.
 */
public final class ClickCooldowns {

    private static Field leftClickCounter;
    private static Field rightClickDelayTimer;
    private static Field isHittingBlock;
    private static boolean resolved;
    private static String report = "not resolved";

    private ClickCooldowns() {
    }

    /** Look the fields up once. Safe to call repeatedly. */
    public static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        StringBuilder sb = new StringBuilder();
        leftClickCounter = field(Minecraft.class, "leftClickCounter", "field_71429_W");
        rightClickDelayTimer = field(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
        isHittingBlock = field(
                net.minecraft.client.multiplayer.PlayerControllerMP.class,
                "isHittingBlock", "field_78778_j");
        sb.append(leftClickCounter != null ? "leftClickCounter ok" : "leftClickCounter MISSING");
        sb.append(", ");
        sb.append(rightClickDelayTimer != null ? "rightClickDelayTimer ok"
                : "rightClickDelayTimer MISSING");
        sb.append(", ");
        sb.append(isHittingBlock != null ? "isHittingBlock ok" : "isHittingBlock MISSING");
        report = sb.toString();
        System.out.println("[PreSight] click cooldowns: " + report);
    }

    private static Field field(Class<?> owner, String mcpName, String srgName) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // try the next name
            } catch (Throwable t) {
                System.err.println("[PreSight] could not open " + name + ": " + t);
                return null;
            }
        }
        return null;
    }

    /**
     * Make the client willing to take another click right now.
     *
     * Called between repeats, not before the burst: the first click is the player's and the
     * game should process it exactly as it always would.
     */
    public static void clear() {
        resolve();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        write(leftClickCounter, mc, 0);
        write(rightClickDelayTimer, mc, 0);
        // clickBlock ignores a second click on the same block while isHittingBlock is set,
        // which is the other half of why a same-tick burst did nothing.
        if (mc.playerController != null) {
            write(isHittingBlock, mc.playerController, false);
        }
    }

    private static void write(Field f, Object target, boolean value) {
        if (f == null || target == null) {
            return;
        }
        try {
            f.setBoolean(target, value);
        } catch (Throwable t) {
            System.err.println("[PreSight] cooldown write failed: " + t);
        }
    }

    private static void write(Field f, Minecraft mc, int value) {
        if (f == null) {
            return;
        }
        try {
            f.setInt(mc, value);
        } catch (Throwable t) {
            System.err.println("[PreSight] cooldown write failed: " + t);
        }
    }

    /** What the fields resolved to, for the log and for the report back to the owner. */
    public static String report() {
        resolve();
        return report;
    }
}
