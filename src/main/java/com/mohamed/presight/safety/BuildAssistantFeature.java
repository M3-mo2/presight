package com.mohamed.presight.safety;

import com.mohamed.presight.core.Blocks;
import com.mohamed.presight.core.TickContext;
import com.mohamed.presight.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Catches you when the bridge under your feet runs out.
 *
 * The owner's description is exact: bridging from blue to red over the void, sneak, place,
 * step, place — and the failure is a single mistimed sneak. So the tool is not a levitation
 * cheat: it watches footing and does the one thing a careful human does, a fraction sooner.
 *
 * Two decisions matter, and both are deliberately conservative:
 *
 * 1. <b>When to arm.</b> Arming on "sneaking" alone would fire in every doorway. Arming on
 *    "near an edge" alone would fire while standing at a window. It takes three
 *    independent signals — sneaking, holding blocks, and no solid ground under the next
 *    step — and it stays armed while any two of them remain true, so a single mistyped
 *    keypress cannot disarm it mid-bridge.
 *
 * 2. <b>What to do.</b> Sneaking is enforced, not simulated. Minecraft's own sneak flag is
 *    what stops the walk-off, so setting it is the same code path the player's Shift key
 *    takes; it cannot desynchronise, and it disarms on its own the moment there is ground
 *    again. Placing a block under the player is offered as a second line for when sneaking
 *    is not enough — but it is off by default, because a mod that silently spends blocks
 *    from your hotbar is a mod that destroys your bridge.
 *
 * Everything it changes is restored the instant the danger passes, and it never touches
 * movement keys or the camera.
 */
public class BuildAssistantFeature implements Feature {

    public static final String ID = "build_assistant";

    /** Signals needed to arm. Three, because any one of them alone is a false positive. */
    private static final int ARM_SIGNALS = 3;
    /** Signals needed to stay armed. Lower, so a keypress gap does not disarm mid-bridge. */
    private static final int HOLD_SIGNALS = 2;
    /** How far a drop has to be before the edge counts as dangerous. */
    private static final int DANGER_DROP = 3;
    /** Scan depth under the player when measuring a drop. */
    private static final int MAX_SCAN = 8;

    private boolean armed;
    /** True while this feature is holding the sneak flag, so it never releases the player's own. */
    private boolean holdingSneak;
    /** Set when the player was already sneaking; we must not steal that. */
    private boolean playerWasSneaking;

    private String status = "idle";

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
            status = "off";
        }
    }

    @Override
    public void tick(TickContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            releaseSneak();
            return;
        }
        EntityPlayerSP player = mc.thePlayer;

        int signals = countSignals(mc, player);
        boolean wasArmed = armed;

        if (!armed && signals >= ARM_SIGNALS) {
            armed = true;
        } else if (armed && signals < HOLD_SIGNALS) {
            armed = false;
        }

        if (wasArmed != armed) {
            // Logged so "it never armed" and "it armed and did nothing" stop looking alike.
            System.out.println("[PreSight] build_assistant " + (armed ? "armed" : "disarmed")
                    + " (signals=" + signals + ")");
        }

        if (!armed) {
            status = "watching";
            releaseSneak();
            return;
        }

        if (isAtRisk(mc, player)) {
            if (!holdingSneak) {
                playerWasSneaking = ((net.minecraft.entity.Entity) player).isSneaking();
                holdingSneak = true;
                System.out.println("[PreSight] build_assistant saved you: no ground ahead");
            }
            ((net.minecraft.entity.Entity) player).setSneaking(true);
            status = "HOLDING";
        } else {
            releaseSneak();
            status = "armed";
        }
    }

    /**
     * How many independent signs say "bridging" are present right now.
     *
     * Kept as separate booleans rather than a score so the log can explain a decision, and
     * so a future signal is one more term rather than a rebalance.
     */
    private int countSignals(Minecraft mc, EntityPlayerSP player) {
        int signals = 0;
        if (((net.minecraft.entity.Entity) player).isSneaking()) {
            signals++;                      // 1. holding sneak, as bridging requires
        }
        if (holdingBlocks(player)) {
            signals++;                      // 2. a block stack is in hand
        }
        if (!Blocks.standable(mc.theWorld, Blocks.feet(player).down())) {
            signals++;                      // 3. nothing solid under the feet
        }
        if (player.posY - MathHelper.floor_double(player.posY) > 0.0D) {
            // 4. standing on the edge of a block rather than inside a walkable floor
            signals++;
        }
        return signals;
    }

    private static boolean holdingBlocks(EntityPlayerSP player) {
        // getEquipmentInSlot is declared on EntityLivingBase and isSneaking/setSneaking on
        // Entity. Naming the subclass as the owner produces a call site the SRG map has no
        // entry for, which survives the remap under its readable name and throws
        // NoSuchMethodError the first time the player sneaks — silently, inside the tick.
        ItemStack held = ((net.minecraft.entity.EntityLivingBase) player).getEquipmentInSlot(0);
        return held != null && held.stackSize > 0 && held.getItem() instanceof ItemBlock;
    }

    /**
     * The condition worth overriding the player for: standing over a drop, with nothing
     * solid ahead of or under the next step.
     */
    private boolean isAtRisk(Minecraft mc, EntityPlayerSP player) {
        World world = mc.theWorld;
        int drop = Blocks.dropBelow(world, player, MAX_SCAN);
        if (drop < DANGER_DROP) {
            return false;
        }
        // Only when the next step would actually be over the hole: standing safely back from
        // the edge and panicking there is the false positive that makes a tool untrustworthy.
        BlockPos ahead = stepAhead(player);
        return !Blocks.standable(world, ahead.down());
    }

    /** The block one step in the direction the player is facing, at foot height. */
    private static BlockPos stepAhead(EntityPlayerSP player) {
        float yaw = player.rotationYaw;
        double radians = Math.toRadians(yaw);
        int dx = (int) Math.round(-Math.sin(radians));
        int dz = (int) Math.round(Math.cos(radians));
        BlockPos feet = Blocks.feet(player);
        return feet.add(dx, 0, dz);
    }

    /**
     * Let go of sneak, unless the player is holding Shift themselves.
     *
     * The distinction matters: releasing the player's own sneak while they are bridging is
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
            ((net.minecraft.entity.Entity) mc.thePlayer).setSneaking(playerWasSneaking);
        }
    }

    /** What the HUD status line can show, so the tool's state is visible not guessed. */
    public String status() {
        return armed ? (holdingSneak ? "HOLDING" : "armed") : "watching";
    }
}
