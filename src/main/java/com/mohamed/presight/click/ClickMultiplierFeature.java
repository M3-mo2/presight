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
            active = false;
            System.out.println("[PreSight] click_multiplier OFF (shift+i)");
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        "\u00a78[\u00a7bPreSight\u00a78] \u00a77Click Multiplier: \u00a7cOFF"));
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
            return;
        }
        // Only while actually in the world and not typing in a screen: a burst fired while a
        // chest is open would click the container, not the world.
        if (ctx.mc.currentScreen != null) {
            leftWasDown = Mouse.isButtonDown(0);
            rightWasDown = Mouse.isButtonDown(1);
            return;
        }

        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);

        boolean fire = (button == Button.LEFT && leftDown && !leftWasDown)
                || (button == Button.RIGHT && rightDown && !rightWasDown);

        leftWasDown = leftDown;
        rightWasDown = rightDown;

        if (fire) {
            bursts++;
            for (int i = 1; i < count; i++) {
                replay(ctx, button);
            }
            if (bursts == 1 || bursts % 20 == 0) {
                System.out.println("[PreSight] click_multiplier: " + button + " x" + count
                        + " (burst " + bursts + ")");
            }
        }
    }

    /** Perform the same action the real click just performed. */
    private void replay(TickContext ctx, Button which) {
        Minecraft mc = ctx.mc;
        EntityPlayerSP p = ctx.player;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (p == null || mc.playerController == null) {
            return;
        }
        try {
            if (which == Button.RIGHT) {
                // getHeldItem is declared on EntityPlayer, not EntityPlayerSP: naming the
                // subclass leaves a call the SRG map has no entry for, which survives the
                // remap as a readable name and throws NoSuchMethodError on the first click.
                ItemStack held = ((net.minecraft.entity.player.EntityPlayer) p).getHeldItem();
                if (held != null) {
                    mc.playerController.sendUseItem(p, ctx.world, held);
                }
                return;
            }
            if (hit != null
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                    && hit.entityHit != null) {
                Entity target = hit.entityHit;
                mc.playerController.attackEntity(p, target);
                p.swingItem();
                return;
            }
            if (hit != null
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                mc.playerController.clickBlock(pos, hit.sideHit);
                p.swingItem();
                return;
            }
            // Nothing under the crosshair: a left click in the air is only an arm swing.
            p.swingItem();
        } catch (Throwable t) {
            // A repeat that fails must never take the click that worked down with it.
            System.err.println("[PreSight] click_multiplier replay failed: " + t);
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
