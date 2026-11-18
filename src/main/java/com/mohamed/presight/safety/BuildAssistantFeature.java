package com.mohamed.presight.safety;

import com.mohamed.presight.core.Blocks;
import com.mohamed.presight.core.TickContext;
import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3i;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Holds sneak for you while you bridge over the void.
 *
 * <h3>The first version did nothing, and the reason is worth keeping</h3>
 *
 * It counted "nothing solid under my feet" as evidence of bridging and required three of
 * four signals before arming. But while bridging you are standing on the block you just
 * placed: there <em>is</em> ground underfoot, and your Y is an exact integer, so two of the
 * four signals are always false and the count never reached three. It could not arm. The
 * owner found that out by falling into the void with Shift released.
 *
 * <h3>What it does instead</h3>
 *
 * Two conditions are mandatory because they are what bridging <em>is</em>:
 *
 * <ul>
 *   <li>you are sneaking, and</li>
 *   <li>a block stack is in your hand.</li>
 * </ul>
 *
 * Then one danger test, against the block you would step onto <em>and</em> two blocks
 * further out. Sampling several positions is what makes it stable: at any instant one is
 * over a placed block and another over air, and the max drop is the honest answer to "am I
 * about to be over a hole". A single-position test flips every step, which means it disarms
 * you mid-bridge.
 *
 * While all three hold, this holds sneak. It keeps holding as long as sneaking and holding
 * blocks remain true, even if the gap momentarily closes — releasing there is the fall.
 *
 * The enforcement is vanilla's own sneak flag: the same code path Shift takes, so it cannot
 * desynchronise, and it lets go the instant you stop bridging. It never releases a sneak you
 * are holding yourself.
 */
public class BuildAssistantFeature implements Feature {

    public static final String ID = "build_assistant";

    /** How far a drop has to be before it counts. Two is a step down; three is death. */
    private static final int DANGER_DROP = 3;
    /** How deep to look for ground. */
    private static final int MAX_SCAN = 8;

    private boolean armed;
    private boolean holdingSneak;
    private boolean playerWasSneaking;

    /** Last printed decision, so the log shows changes rather than sixty lines a second. */
    private String lastDiag = "";

    /** Last decision inputs, so the HUD can show why it is or is not acting. */
    private boolean lastSneaking;
    private boolean lastHolding;
    private int lastDrop;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Build Assistant";
    }

    @Override
    public int defaultKey() {
        return Keyboard.KEY_U;
    }

    @Override
    public void onToggle(boolean enabled) {
        if (!enabled) {
            releaseSneak();
            armed = false;
        }
    }

    @Override
    public void tick(TickContext ctx) {
        Minecraft mc = ctx.mc;
        EntityPlayerSP p = ctx.player;
        World world = ctx.world;
        if (p == null || world == null) {
            releaseSneak();
            return;
        }

        boolean sneaking = ((Entity) p).isSneaking();
        boolean holding = holdingBlocks(p);
        int drop = dangerDropAhead(p, world);
        lastSneaking = sneaking;
        lastHolding = holding;
        lastDrop = drop;

        boolean wasArmed = armed;
        if (!armed) {
            // Arming takes all three: this is where a doorway or a window differs from a bridge.
            armed = sneaking && holding && drop >= DANGER_DROP;
        } else if (!holding || drop < DANGER_DROP) {
            // Disarming takes only blocks or the gap closing.
            //
            // `sneaking` is deliberately NOT a disarm condition. Releasing Shift is the exact
            // failure this tool exists for; disarming on it made the tool let go at the moment
            // the player lost their grip — which is how the owner fell into the void with the
            // feature on and ARMED one tick earlier.
            armed = false;
        }

        if (armed != wasArmed) {
            System.out.println("[PreSight] build_assistant " + (armed ? "ARMED" : "disarmed")
                    + " (sneak=" + sneaking + " blocks=" + holding + " drop=" + drop + ")");
        }

        if (!armed) {
            releaseSneak();
            diag(sneaking, holding, drop);
            return;
        }

        if (!holdingSneak) {
            playerWasSneaking = sneaking;
            holdingSneak = true;
            System.out.println("[PreSight] build_assistant holding sneak (drop=" + drop + ")");
        }
        ((Entity) p).setSneaking(true);
        diag(sneaking, holding, drop);
    }

    /**
     * The largest drop found under the player or the next two blocks ahead.
     *
     * "Ahead" is the facing direction rounded to a block step, which is how walking works in
     * this game — you move along one axis, so rounding is not an approximation here.
     */
    private int dangerDropAhead(EntityPlayerSP p, World world) {
        BlockPos feet = Blocks.feet(p);
        int best = Blocks.dropBelow(world, p, MAX_SCAN);

        double radians = Math.toRadians(p.rotationYaw);
        int dx = (int) Math.round(-Math.sin(radians));
        int dz = (int) Math.round(Math.cos(radians));

        for (int step = 1; step <= 2; step++) {
            BlockPos ahead = feet.add(dx * step, 0, dz * step);
            int d = dropAt(world, ahead);
            if (d > best) {
                best = d;
            }
        }
        return best;
    }

    /** Drop under one block position: walk down until something standable. */
    private static int dropAt(World world, BlockPos pos) {
        for (int d = 1; d <= MAX_SCAN; d++) {
            BlockPos probe = pos.down(d);
            if (Blocks.standable(world, probe)) {
                return d - 1;
            }
            // getY is declared on Vec3i, which BlockPos extends. Calling it with BlockPos as
            // the owner leaves a name the SRG map does not have, and that is a
            // NoSuchMethodError at runtime.
            if (Blocks.isVoid(world, ((Vec3i) probe).getY())) {
                return MAX_SCAN;
            }
        }
        return MAX_SCAN;
    }

    private static boolean holdingBlocks(EntityPlayerSP player) {
        // getEquipmentInSlot is declared on EntityLivingBase, and isSneaking/setSneaking on
        // Entity. Naming the subclass as the owner produces a call the SRG map has no entry
        // for, which survives the remap under its readable name and throws
        // NoSuchMethodError — silently, inside the tick.
        ItemStack held = ((EntityLivingBase) player).getEquipmentInSlot(0);
        return held != null && held.stackSize > 0 && held.getItem() instanceof ItemBlock;
    }

    /**
     * One line per decision change.
     *
     * The first version failed silently and cost a death, so the decision is printed. This is
     * what tells "never armed" apart from "armed and the drop read as small".
     */
    private void diag(boolean sneaking, boolean holding, int drop) {
        String now = "sneak=" + sneaking + " blocks=" + holding + " drop=" + drop
                + " armed=" + armed + " holding=" + holdingSneak;
        if (now.equals(lastDiag)) {
            return;
        }
        lastDiag = now;
        System.out.println("[PreSight] build_assistant: " + now);
    }

    /**
     * Let go of sneak, unless the player is holding Shift themselves.
     *
     * That distinction matters: releasing the player's own sneak while they are bridging is
     * exactly the death this feature exists to prevent.
     */
    private void releaseSneak() {
        if (!holdingSneak) {
            return;
        }
        holdingSneak = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        boolean playerHoldsShift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!playerHoldsShift) {
            ((Entity) mc.thePlayer).setSneaking(playerWasSneaking);
        }
    }

    /** What the HUD status line can show, so the tool's state is visible rather than guessed. */
    public String status() {
        return armed ? (holdingSneak ? "HOLDING" : "armed") : "watching";
    }

    /**
     * One compact line for the HUD: the three inputs and the decision.
     *
     * The three inputs matter as much as the verdict. "U: no" on its own says the tool did
     * nothing; "U: s=true b=true d=8" says exactly which of the three conditions was missing,
     * which is the difference between a bug report and a fix.
     */
    public String hudLine() {
        if (!FeatureRegistry.isEnabled(ID)) {
            return "U: off";
        }
        return "U: " + (holdingSneak ? "HOLDING" : armed ? "armed" : "wait")
                + " s=" + (lastSneaking ? "1" : "0")
                + " b=" + (lastHolding ? "1" : "0")
                + " d=" + lastDrop;
    }
}
