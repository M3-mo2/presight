package com.mohamed.presight.client;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.render.BoxBatch;
import com.mohamed.presight.render.TextBatch;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The world render pass: one begin, every feature, one flush.
 *
 * The single shared batch is not a micro-optimisation. A Tessellator flush per box means
 * one submission per player per frame; on a full server that alone takes the frame rate
 * to single digits, and the visible symptom is "the mod lags and the game looks wrong".
 *
 * The batch is flushed in a finally block, always. An unflushed batch leaves an open draw
 * call and every later draw in the frame lands inside it.
 */
public class WorldRenderHandler {

    private final BoxBatch boxes = new BoxBatch();
    private final TextBatch texts = new TextBatch();
    private boolean announced;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.fontRendererObj == null) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        if (rm == null) {
            return;
        }

        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            viewer = mc.thePlayer;
        }

        // RenderManager already computed exactly this for the frame; using its values
        // instead of recomputing removes a class of small drift.
        float pt = event.partialTicks;
        double camX = rm.viewerPosX;
        double camY = rm.viewerPosY;
        double camZ = rm.viewerPosZ;
        if (camX == 0.0D && camY == 0.0D && camZ == 0.0D) {
            camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
            camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
            camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        }

        RenderContext ctx = new RenderContext(camX, camY, camZ, pt,
                rm.playerViewY, rm.playerViewX, boxes, texts);

        if (!announced) {
            announced = true;
            System.out.println("[PreSight] render pass is live (players="
                    + mc.theWorld.playerEntities.size() + ", enabled=" + enabledList() + ")");
        }

        boxes.begin(2.0F);
        try {
            for (Feature f : FeatureRegistry.all()) {
                if (!FeatureRegistry.isEnabled(f.id())) {
                    continue;
                }
                try {
                    f.renderWorld(ctx);
                } catch (Throwable t) {
                    System.err.println("[PreSight] world '" + f.id() + "' threw:");
                    t.printStackTrace();
                    Draw.forceSaneState();
                }
            }
        } finally {
            // Order matters: the line batch closes first, because text has to change GL
            // state and none of that is legal between glBegin and glEnd.
            boxes.flush();
            texts.draw(ctx);
        }
    }

    private static String enabledList() {
        StringBuilder sb = new StringBuilder();
        for (Feature f : FeatureRegistry.all()) {
            if (FeatureRegistry.isEnabled(f.id())) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(f.id());
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }
}
