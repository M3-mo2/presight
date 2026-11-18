package com.mohamed.presight.gear;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.Projection;
import com.mohamed.presight.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Shows a player's visible gear beside their box.
 *
 * Which player: whoever you are looking at, and if you are looking at nobody, the
 * nearest one — so pressing Y always does something instead of silently nothing.
 *
 * What it shows: a 2D inventory-style panel drawn where the player is on screen,
 * with each item rendered by the game's own RenderItem so a diamond sword looks
 * like a diamond sword and a stack of 64 shows its count. Only the slots a client
 * is actually told about exist: held item and the four armour slots.
 */
public class GearViewFeature implements Feature {

    public static final String ID = "gear_view";

    private static final int CELL = 18;          // icon cell, the same 18px the inventory uses
    private static final int PAD = 4;
    private static final int BG = 0xC8101014;
    private static final int BORDER = 0xFF3CD07A;

    /** The player we last drew for, so the key and the renderer agree. */
    private EntityPlayer target;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gear View";
    }

    @Override
    public int defaultKey() {
        return Keyboard.KEY_Y;
    }

    @Override
    public void onToggle(boolean enabled) {
        if (!enabled) {
            target = null;
        }
    }

    @Override
    public void renderWorld(RenderContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        target = pickTarget(mc);
        if (target == null) {
            return;
        }

        GearSnapshot gear = GearSnapshot.of(target);
        List<ItemStack> items = gear.presentItems();

        int cols = Math.max(1, items.size());
        int panelW = PAD * 2 + cols * CELL;
        int panelH = PAD * 2 + CELL + 10;

        // Project the player's head to screen space. Doing it through Minecraft's own
        // projection keeps the panel glued to the player when the camera moves, which
        // a hand-rolled matrix multiply usually gets subtly wrong.
        double[] screen = projectToScreen(mc, ctx, target);
        if (screen == null) {
            return;
        }
        int px = (int) screen[0];
        int py = (int) screen[1] - panelH - 14;

        ScaledResolution res = new ScaledResolution(mc);
        px = Math.max(2, Math.min(px - panelW / 2, res.getScaledWidth() - panelW - 2));
        py = Math.max(2, Math.min(py, res.getScaledHeight() - panelH - 2));

        // Overlay pass: switch to screen coordinates for the panel, draw the icons,
        // then the label. The matrix is restored before the next entity is drawn.
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);          // above the world and the HUD
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        Draw.rect(px, py, panelW, panelH, BG);
        Draw.outline(px, py, panelW, panelH, BORDER);

        FontRenderer font = mc.fontRendererObj;
        font.drawStringWithShadow(gear.playerName, px + PAD, py + PAD, 0xFF9BFF6B);

        RenderHelper.enableGUIStandardItemLighting();
        RenderItem ri = mc.getRenderItem();
        int ix = px + PAD;
        int iy = py + PAD + 11;
        for (ItemStack stack : items) {
            ri.renderItemAndEffectIntoGUI(stack, ix, iy);
            ri.renderItemOverlayIntoGUI(font, stack, ix, iy, null);
            ix += CELL;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();

        // And the box, so the panel is obviously attached to a person.
        double half = target.width / 2.0D;
        Draw.box(ctx.relX(target) - half, ctx.relY(target), ctx.relZ(target) - half,
                 ctx.relX(target) + half, ctx.relY(target) + target.height, ctx.relZ(target) + half,
                 0.23F, 0.82F, 0.48F, 1.0F, 1.5F);
    }

    /**
     * The player being looked at, else the nearest one within reach of the ray.
     * Returns null when the world has no other players.
     */
    private static EntityPlayer pickTarget(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityPlayer
                && hit.entityHit != mc.thePlayer) {
            return (EntityPlayer) hit.entityHit;
        }

        // Nothing under the crosshair: ray-cast a generous distance so the feature is
        // useful in a fight where you are not precisely aiming at anyone.
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * 48.0D, look.yCoord * 48.0D, look.zCoord * 48.0D);
        AxisAlignedBB area = mc.thePlayer.getEntityBoundingBox()
                .addCoord(look.xCoord * 48.0D, look.yCoord * 48.0D, look.zCoord * 48.0D)
                .expand(4.0D, 4.0D, 4.0D);

        EntityPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.theWorld.getEntitiesWithinAABB(EntityPlayer.class, area)) {
            if (e == mc.thePlayer || !(e instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) e;
            AxisAlignedBB bb = p.getEntityBoundingBox().expand(0.3D, 0.3D, 0.3D);
            MovingObjectPosition mop = bb.calculateIntercept(eye, end);
            if (mop == null) {
                continue;
            }
            double d = eye.distanceTo(mop.hitVec);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Where the player's head lands on the (scaled) screen, or null when behind the
     * camera. Projection uses Minecraft's live matrices, so it matches the world pass.
     */
    private static double[] projectToScreen(Minecraft mc, RenderContext ctx, Entity e) {
        ScaledResolution res = new ScaledResolution(mc);
        return Projection.worldToScreen(ctx.relX(e), ctx.relY(e) + e.height + 0.2D,
                ctx.relZ(e), res.getScaledWidth(), res.getScaledHeight());
    }
}
