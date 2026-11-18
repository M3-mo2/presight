package com.mohamed.presight.gear;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.Projection;
import com.mohamed.presight.render.RenderContext;
import com.mohamed.presight.render.ScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Shows a player's visible gear in a panel beside their box.
 *
 * The work is split across the two passes on purpose. Drawing screen-space content
 * from the world pass is wrong twice: the 3D perspective matrix is still bound, so a
 * "small panel" becomes a giant quad out in the world — that is what made the screen
 * go white — and the projection needed to find where a player is on screen only works
 * while the world matrices are live.
 *
 * So the world pass picks the target, draws the box, and computes the screen position;
 * the HUD pass draws the panel with the 2D projection Minecraft has already set up.
 */
public class GearViewFeature implements Feature {

    public static final String ID = "gear_view";

    private static final int CELL = 18;      // icon cell, the same 18px the inventory uses
    private static final int PAD = 4;
    private static final int BG = 0xC8101014;
    private static final int BORDER = 0xFF3CD07A;
    private static final int LABEL = 0xFF9BFF6B;

    /** Panel placement, computed in the world pass and read by the HUD pass. */
    private int screenX = -1;
    private int screenY = -1;
    private int panelW;
    private int panelH;
    private String name;
    private ItemStack[] icons;

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
            screenX = -1;
            screenY = -1;
            name = null;
            icons = null;
        }
    }

    // ----------------------------------------------------------------- world pass

    @Override
    public void renderWorld(RenderContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        EntityPlayer target = pickTarget(mc);
        if (target == null) {
            screenX = -1;
            return;
        }

        // The box belongs to the 3D pass; it is the only thing drawn here.
        double half = target.width / 2.0D;
        ctx.boxes.colour(0.23F, 0.82F, 0.48F, 1.0F);
        ctx.boxes.addBox(ctx.relX(target) - half, ctx.relY(target), ctx.relZ(target) - half,
                ctx.relX(target) + half, ctx.relY(target) + target.height,
                ctx.relZ(target) + half);

        GearSnapshot gear = GearSnapshot.of(target);
        List<ItemStack> items = gear.presentItems();

        // Projected here, while the world matrices are current. gluProject reads the
        // live matrices, so the same call in the HUD pass would project against the 2D
        // projection and put the panel somewhere useless.
        ScaledResolution res = new ScaledResolution(mc);
        double[] screen = Projection.worldToScreen(
                ctx.relX(target), ctx.relY(target) + target.height + 0.35D, ctx.relZ(target),
                res.getScaledWidth(), res.getScaledHeight());

        if (screen == null) {
            screenX = -1;   // behind the camera; drawing it would put the panel anywhere
            return;
        }

        int cols = Math.max(1, items.size());
        panelW = PAD * 2 + cols * CELL;
        panelH = PAD * 2 + 10 + CELL;

        int px = (int) screen[0] - panelW / 2;
        int py = (int) screen[1] - panelH - 6;
        screenX = Math.max(2, Math.min(px, res.getScaledWidth() - panelW - 2));
        screenY = Math.max(2, Math.min(py, res.getScaledHeight() - panelH - 2));

        name = gear.playerName;
        icons = items.toArray(new ItemStack[0]);
    }

    // ------------------------------------------------------------------- HUD pass

    @Override
    public void renderScreen(ScreenContext ctx) {
        if (screenX < 0 || name == null) {
            return;
        }

        // The panel is drawn even when the player is carrying nothing: "no gear" is a
        // real answer, and an invisible panel reads as the feature being broken. Only
        // the icon row is skipped.
        ctx.rect(screenX, screenY, panelW, panelH, BG);
        ctx.outline(screenX, screenY, panelW, panelH, BORDER);
        ctx.text(name, screenX + PAD, screenY + PAD, LABEL);

        if (icons == null || icons.length == 0) {
            return;
        }

        // The game's own item renderer, so a diamond sword looks like a diamond sword and
        // a stack of 64 shows its count — the same call the inventory makes.
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem ri = Minecraft.getMinecraft().getRenderItem();
        int ix = screenX + PAD;
        int iy = screenY + PAD + 10;
        for (ItemStack stack : icons) {
            if (stack == null) {
                continue;
            }
            ri.renderItemAndEffectIntoGUI(stack, ix, iy);
            ri.renderItemOverlayIntoGUI(fontOf(), stack, ix, iy, null);
            ix += CELL;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static FontRenderer fontOf() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    // -------------------------------------------------------------------- target

    /**
     * The player under the crosshair, else the nearest one along the view ray.
     * Null when nobody qualifies.
     */
    private static EntityPlayer pickTarget(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityPlayer
                && hit.entityHit != mc.thePlayer) {
            return (EntityPlayer) hit.entityHit;
        }

        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * 48.0D, look.yCoord * 48.0D, look.zCoord * 48.0D);
        AxisAlignedBB area = mc.thePlayer.getEntityBoundingBox()
                .addCoord(look.xCoord * 48.0D, look.yCoord * 48.0D, look.zCoord * 48.0D)
                .expand(4.0D, 4.0D, 4.0D);

        EntityPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (Object o : mc.theWorld.getEntitiesWithinAABB(EntityPlayer.class, area)) {
            if (!(o instanceof EntityPlayer) || o == mc.thePlayer) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
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
}
