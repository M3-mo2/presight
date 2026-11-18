package com.mohamed.presight.gear;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.render.Projection;
import com.mohamed.presight.render.RenderContext;
import com.mohamed.presight.render.ScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a player's visible gear in a panel beside their box.
 *
 * Split across both passes on purpose. Screen-space drawing from the world pass is wrong
 * twice: the 3D perspective matrix is still bound there, so a "small panel" becomes a giant
 * quad out in the world, and the projection needed to find where a player is on screen only
 * works while the world matrices are live.
 *
 * Target selection is cached. Ray-casting against every player every frame is what made the
 * game stutter whenever the cursor crossed a player: the bounding-box query allocates a box
 * per candidate and it ran sixty times a second over a long cone. The crosshair result is
 * used when it exists — Minecraft already computed it for this frame — and the expensive
 * scan runs at most once every SCAN_INTERVAL ticks.
 */
public class GearViewFeature implements Feature {

    public static final String ID = "gear_view";

    // 16 is the smallest cell that still fits a 16x16 item icon without clipping, so the
    // card is as small as the icons allow rather than padded for its own sake.
    private static final int CELL = 16;
    private static final int PAD = 2;
    private static final int MIN_COLS = 2;   // stops the panel collapsing when empty
    private static final int BG = 0xC8101014;
    private static final int BORDER = 0xFF3CD07A;
    private static final int MUTED = 0xFF808080;

    /** Re-run the expensive scan at most this often, in client ticks. */
    private static final int SCAN_INTERVAL = 6;

    /** How far away a player can be and still get a panel, in blocks. */
    private static final double PANEL_RANGE = 32.0D;

    /**
     * A jump larger than this in one frame is a teleport, not walking.
     *
     * Without it the card follows a player who blinked across the map: it renders for a
     * frame at a position that has nothing to do with anything visible, which reads as a
     * ghost panel. A sprint is under 0.4 blocks a tick, so 8 leaves plenty of room.
     */
    private static final double TELEPORT_JUMP = 8.0D;

    /** How long the card stays hidden after a teleport, in client ticks (2 s). */
    private static final int TELEPORT_GRACE_TICKS = 40;

    /** One scan diagnostic per session; enough to answer "why no panel". */
    private static boolean loggedScan;

    /** Panel placement, computed in the world pass and read by the HUD pass. */
    private int screenX = -1;
    private int screenY = -1;
    private int panelW;
    private int panelH;
    private String name;
    private ItemStack[] icons;

    /** Carried from renderWorld to measureWorld, so the batch can close first. */
    /** Counts frames with no target, so the log line is occasional not spammy. */
    private int framesWithoutTarget;

    private String lastLoggedName;

    private EntityPlayer pendingTarget;
    private List<ItemStack> pendingItems;

    /** Exposed so the HUD status line can say what the panel is tracking. */
    public String targetName() {
        return name;
    }

    /** Cached scan result and the tick it was taken. */
    private EntityPlayer cachedTarget;
    private int lastScanTick;

    /** Where the subject was last frame, for teleport detection. */
    private Vec3 lastSeenPosition;

    /** Tick until which the card stays hidden after a teleport. */
    private int suppressUntilTick;

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
            cachedTarget = null;
            pendingTarget = null;
            pendingItems = null;
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
        if (target == null || !isPanelSubjectValid(mc, target)) {
            clearCard();
            return;
        }

        double half = target.width / 2.0D;
        ctx.boxes.colour(0.23F, 0.82F, 0.48F, 1.0F);
        ctx.boxes.addBox(ctx.relX(target) - half, ctx.relY(target), ctx.relZ(target) - half,
                ctx.relX(target) + half, ctx.relY(target) + target.height,
                ctx.relZ(target) + half);

        GearSnapshot gear = GearSnapshot.of(target);
        List<ItemStack> items = gear.presentItems();
        this.pendingTarget = target;
        this.pendingItems = items;
    }

    /**
     * The projection, run after the line batch has closed.
     *
     * `Projection.worldToScreen` calls glGetFloat for the modelview and projection
     * matrices, and glGet* between glBegin and glEnd is GL_INVALID_OPERATION (1282) — the
     * error the owner reported on every frame. Doing it here keeps the ordering honest
     * instead of hoping nobody reads GL state while a primitive is open.
     */
    @Override
    public void measureWorld(RenderContext ctx) {
        EntityPlayer target = pendingTarget;
        if (framesWithoutTarget == 0) {
            // Unconditional proof that this hook runs at all: an empty log after this line
            // means the handler never reached the measure phase, which is a different bug
            // from a null target.
            System.out.println("[PreSight] gear: measureWorld entered, pending="
                    + (target == null ? "null" : target.getName()));
        }
        if (target == null || !FeatureRegistry.isEnabled(ID)) {
            if (framesWithoutTarget++ % 200 == 0) {
                // Said out loud because "no panel" has two very different causes: the
                // crosshair is not on a player, or the projection failed. Without this
                // line they are the same observation, and they need opposite fixes.
                // The eye/look pair is printed so the aim can be reproduced exactly
                // instead of hunted for by hand.
                Minecraft mc0 = Minecraft.getMinecraft();
                String aim = "";
                if (mc0.thePlayer != null) {
                    net.minecraft.util.Vec3 e = mc0.thePlayer.getPositionEyes(1.0F);
                    net.minecraft.util.Vec3 l = mc0.thePlayer.getLook(1.0F);
                    aim = String.format(" eye=%.1f,%.1f,%.1f look=%.2f,%.2f,%.2f",
                            e.xCoord, e.yCoord, e.zCoord, l.xCoord, l.yCoord, l.zCoord);
                }
                System.out.println("[PreSight] gear: no target under the crosshair" + aim);
            }
            clearCard();
            return;
        }
        List<ItemStack> items = pendingItems == null ? new ArrayList<ItemStack>() : pendingItems;

        Minecraft mc = Minecraft.getMinecraft();
        // No ScaledResolution at all. Its (Minecraft) constructor is absent from 1.8's SRG
        // map — it is a 1.8.9 addition — so the call survived the remap under its readable
        // name and threw NoSuchMethodError inside the render loop on the version the owner
        // plays. That is why the panel never appeared even with the crosshair on a player.
        //
        // gluProject already returns framebuffer pixels, so the result is stored in those
        // and converted to GUI pixels by the HUD pass, which knows the scaled size.
        double[] screen = Projection.worldToScreen(
                ctx.relX(target), ctx.relY(target) + target.height + 0.35D, ctx.relZ(target),
                mc.displayWidth, mc.displayHeight);

        if (screen == null) {
            screenX = -1;   // behind the camera
            return;
        }

        // Convert framebuffer pixels to the coordinates Gui.drawRect uses.
        int guiScale = mc.gameSettings.guiScale;
        if (guiScale == 0) {
            guiScale = 1000;   // "auto"
        }
        int scaleFactor = 1;
        while (scaleFactor < guiScale && mc.displayWidth / (scaleFactor + 1) >= 320
                && mc.displayHeight / (scaleFactor + 1) >= 240) {
            scaleFactor++;
        }

        int cols = Math.max(MIN_COLS, items.size());
        panelW = PAD * 2 + cols * CELL;
        // No name row: the icons are the content, and a name row made the card taller for
        // text that duplicates the nameplate Minecraft already draws above the player.
        panelH = PAD * 2 + CELL;

        int guiW = mc.displayWidth / scaleFactor;
        int guiH = mc.displayHeight / scaleFactor;
        int px = (int) (screen[0] / scaleFactor) - panelW / 2;
        int py = (int) (screen[1] / scaleFactor) - panelH - 6;
        screenX = Math.max(2, Math.min(px, guiW - panelW - 2));
        screenY = Math.max(2, Math.min(py, guiH - panelH - 2));

        name = target.getName();
        icons = items.toArray(new ItemStack[0]);

        if (!name.equals(lastLoggedName)) {
            lastLoggedName = name;
            // The one line that separates "the panel is placed wrongly" from "the panel
            // was never placed": it names the target, the panel rectangle, and the items.
            System.out.println("[PreSight] gear: panel for " + name
                    + " at (" + screenX + "," + screenY + ") " + panelW + "x" + panelH
                    + " items=" + icons.length);
        }
    }

    /**
     * Whether the card should exist at all this frame.
     *
     * The owner asked for it to disappear when the player dies or teleports, and the reason
     * is the same in both cases: the card points at a place. A corpse and a player who
     * blinked away are both "nothing to point at", and a card hanging over empty ground
     * reads as a bug — worse than no card.
     *
     * After a teleport the card stays hidden for a short grace period rather than
     * reappearing on the very next frame at the destination, because a panel that jumps
     * across the screen along with the player is the same ghost one frame later.
     */
    private boolean isPanelSubjectValid(Minecraft mc, EntityPlayer target) {
        if (target.isDead || target.getHealth() <= 0.0F) {
            lastSeenPosition = null;
            return false;
        }
        // Removed from the world entirely (left, died and despawned, changed dimension).
        if (!mc.theWorld.playerEntities.contains(target)) {
            lastSeenPosition = null;
            return false;
        }

        Vec3 now = new Vec3(target.posX, target.posY, target.posZ);
        if (lastSeenPosition != null) {
            double jump = now.distanceTo(lastSeenPosition);
            if (jump > TELEPORT_JUMP) {
                // Hide, and remember the new place so the grace period is only entered once.
                suppressUntilTick = mc.thePlayer.ticksExisted + TELEPORT_GRACE_TICKS;
                cachedTarget = null;
                lastSeenPosition = now;
                return false;
            }
        }
        lastSeenPosition = now;

        if (mc.thePlayer.ticksExisted < suppressUntilTick) {
            return false;
        }
        return true;
    }

    /** Drop everything the HUD pass reads, so nothing is drawn from stale state. */
    private void clearCard() {
        screenX = -1;
        screenY = -1;
        name = null;
        icons = null;
        pendingTarget = null;
        pendingItems = null;
    }

    // ------------------------------------------------------------------- HUD pass

    @Override
    public void renderScreen(ScreenContext ctx) {
        if (screenX < 0 || name == null) {
            return;
        }

        // Drawn even when the target carries nothing: "no gear" is a real answer, and an
        // invisible panel reads as the feature being broken.
        ctx.rect(screenX, screenY, panelW, panelH, BG);
        ctx.outline(screenX, screenY, panelW, panelH, BORDER);

        if (icons == null || icons.length == 0) {
            // "no gear" stays: an empty frame with no explanation reads as a broken card
            // rather than as an accurate "carrying nothing".
            ctx.textCentred("no gear", screenX + panelW / 2, screenY + PAD + 4, MUTED);
            return;
        }

        // The game's own item renderer, so a diamond sword looks like a diamond sword and a
        // stack of 64 shows its count — the same call the inventory makes.
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem ri = Minecraft.getMinecraft().getRenderItem();
        int ix = screenX + PAD;
        int iy = screenY + PAD;
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
     * The player being looked at, or null.
     *
     * Cheap path first: Minecraft already ray-traced the crosshair for this frame, so an
     * entity hit there costs nothing to use. The expensive scan is throttled and cached,
     * because running it every frame is exactly what caused the stutter when the cursor was
     * near a player.
     */
    private EntityPlayer pickTarget(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityPlayer
                && hit.entityHit != mc.thePlayer) {
            cachedTarget = (EntityPlayer) hit.entityHit;
            lastScanTick = mc.thePlayer.ticksExisted;
            return cachedTarget;
        }

        int now = mc.thePlayer.ticksExisted;
        boolean stale = cachedTarget == null
                || cachedTarget.isDead
                || !mc.theWorld.playerEntities.contains(cachedTarget)
                || now - lastScanTick >= SCAN_INTERVAL;

        if (!stale) {
            return cachedTarget;
        }

        lastScanTick = now;
        cachedTarget = scanForTarget(mc);
        return cachedTarget;
    }

    /**
     * The player whose gear to show: whatever the crosshair is on, otherwise the nearest one.
     *
     * Requiring the crosshair to be exactly on a player made the feature look dead: the
     * owner pressed Y, the chat said ON, and no panel appeared — because a 0.8-block-wide
     * hitbox is a hard thing to land on with a mouse. Worse, the ask was explicitly "even
     * the far ones", so aiming precision was never the requirement.
     *
     * So: precise aim wins when it exists, and otherwise the nearest player within
     * PANEL_RANGE gets the panel — the one the ESP box already makes obvious.
     */
    private static EntityPlayer scanForTarget(Minecraft mc) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * 32.0D, look.yCoord * 32.0D, look.zCoord * 32.0D);

        // Players inside the panel range, regardless of where the camera points.
        AxisAlignedBB wide = mc.thePlayer.getEntityBoundingBox().expand(
                PANEL_RANGE, PANEL_RANGE, PANEL_RANGE);

        EntityPlayer aimed = null;
        double aimedDist = Double.MAX_VALUE;
        EntityPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Object o : mc.theWorld.getEntitiesWithinAABB(EntityPlayer.class, wide)) {
            if (!(o instanceof EntityPlayer) || o == mc.thePlayer) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            double toPlayer = eye.distanceTo(
                    new Vec3(p.posX, p.posY + p.height / 2.0D, p.posZ));
            if (toPlayer > PANEL_RANGE) {
                continue;
            }
            if (toPlayer < nearestDist) {
                nearestDist = toPlayer;
                nearest = p;
            }

            AxisAlignedBB bb = p.getEntityBoundingBox().expand(0.3D, 0.3D, 0.3D);
            MovingObjectPosition mop = bb.calculateIntercept(eye, end);
            if (mop == null) {
                continue;
            }
            double d = eye.distanceTo(mop.hitVec);
            if (d < aimedDist) {
                aimedDist = d;
                aimed = p;
            }
        }

        EntityPlayer best = aimed != null ? aimed : nearest;
        if (!loggedScan) {
            loggedScan = true;
            // Names the two possible failures, which look identical on screen: no player
            // inside the ray's bounding box at all, or players there whose hitbox the ray
            // missed. Those need different fixes, so the log says which one happened.
            System.out.println("[PreSight] gear scan: inWorld=" + mc.theWorld.playerEntities.size()
                    + " chosen=" + (best == null ? "none" : best.getName())
                    + " aimed=" + (aimed != null)
                    + " eye=" + String.format("%.1f,%.1f,%.1f",
                        eye.xCoord, eye.yCoord, eye.zCoord)
                    + " look=" + String.format("%.2f,%.2f,%.2f",
                        look.xCoord, look.yCoord, look.zCoord));
        }
        return best;
    }
}
