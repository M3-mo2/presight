package com.mohamed.presight.click;

import com.mohamed.presight.core.TickContext;
import com.mohamed.presight.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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

    /** Which button is being multiplied. */
    public enum Button {
        LEFT, RIGHT
    }

    private Button button = Button.LEFT;
    private int count = 3;
    private boolean active;

    /** Whether the real button was down last tick, for edge detection. */
    private boolean leftWasDown;
    private boolean rightWasDown;

    /** Bursts fired, for the diagnostic line. */
    private int bursts;

    /** Repeats still owed from the last press; one is delivered per tick. */
    private int pending;

    /** Repeats that the game actually accepted, printed once per burst. */
    private int delivered;

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

    @Override
    public void tick(TickContext ctx) {
        if (!active) {
            leftWasDown = Mouse.isButtonDown(0);
            rightWasDown = Mouse.isButtonDown(1);
            pending = 0;
            return;
        }
        // Only while actually in the world and not typing in a screen: a burst fired while a
        // chest is open would click the container, not the world.
        if (ctx.mc.currentScreen != null) {
            leftWasDown = Mouse.isButtonDown(0);
            rightWasDown = Mouse.isButtonDown(1);
            pending = 0;
            return;
        }

        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);

        boolean pressed = (button == Button.LEFT && leftDown && !leftWasDown)
                || (button == Button.RIGHT && rightDown && !rightWasDown);

        leftWasDown = leftDown;
        rightWasDown = rightDown;

        if (pressed) {
            // Queue the repeats instead of running them here.
            //
            // Everything in one tick was the bug: the game applies its own click for this tick
            // first, then refuses a second action on the same block from the same tick, so the
            // burst produced nothing at all and the tool looked dead while reporting ON.
            // One repeat per tick is the fastest rate the game will actually honour.
            pending = count - 1;
            bursts++;
            if (bursts <= 3 || bursts % 25 == 0) {
                // Printed the moment the press is seen, before any replay. If the owner's log
                // has no line here, the tool never saw the click at all — a different fault
                // from "it saw it and the repeat was refused", and the two need different fixes.
                System.out.println("[PreSight] click_multiplier: press #" + bursts
                        + " detected (" + button + "), queueing " + (count - 1) + " repeat(s)");
            }
        }

        if (pending > 0) {
            if (replay(ctx, button)) {
                delivered++;
            }
            pending--;
            if (pending == 0) {
                // The number that matters: how many repeats the game took, not how many were
                // attempted. A burst that reports 0 here means the controller refused them.
                System.out.println("[PreSight] click_multiplier: press -> " + (1 + delivered)
                        + " action(s) accepted of " + count + " requested");
                delivered = 0;
            }
        }
    }

    /** Perform the same action the real click just performed. */
    private boolean replay(TickContext ctx, Button which) {
        Minecraft mc = ctx.mc;
        EntityPlayerSP p = ctx.player;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (p == null || mc.playerController == null) {
            return false;
        }
        try {
            if (which == Button.RIGHT) {
                // getHeldItem is declared on EntityPlayer, not EntityPlayerSP: naming the
                // subclass leaves a call the SRG map has no entry for, which survives the
                // remap as a readable name and throws NoSuchMethodError on the first click.
                ItemStack held = ((net.minecraft.entity.player.EntityPlayer) p).getHeldItem();
                if (held == null) {
                    return false;
                }
                return mc.playerController.sendUseItem(p, ctx.world, held);
            }
            if (hit != null
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                    && hit.entityHit != null) {
                Entity target = hit.entityHit;
                mc.playerController.attackEntity(p, target);
                p.swingItem();
                return true;
            }
            if (hit != null
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                boolean ok = mc.playerController.clickBlock(pos, hit.sideHit);
                p.swingItem();
                return ok;
            }
            // Nothing under the crosshair: a left click in the air is only an arm swing.
            p.swingItem();
            return false;
        } catch (Throwable t) {
            // A repeat that fails must never take the click that worked down with it.
            System.err.println("[PreSight] click_multiplier replay failed: " + t);
            return false;
        }
    }

    public String describe() {
        return active ? (button + " x" + count) : "off";
    }

    /** Exposed so the HUD status line can show the mode without a screen. */
    public String status() {
        return describe();
    }
}
