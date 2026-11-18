package com.mohamed.presight.client;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.RenderContext;
import com.mohamed.presight.render.ScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The world render pass.
 *
 * A public top-level class on purpose. Forge 1.8.9's EventBus discovers handlers by
 * reflection over the registered instance's class; keeping listeners public and
 * top-level removes one whole category of "registered but never called" from the
 * picture, which is otherwise invisible — the registration succeeds and nothing warns.
 *
 * It also logs the first time it runs. Without that, "no box on screen" cannot be
 * distinguished from "this handler never fired", and those need opposite fixes.
 */
public class WorldRenderHandler {

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

        if (!announced) {
            announced = true;
            System.out.println("[PreSight] render pass is live (world=" + mc.theWorld.getClass().getSimpleName()
                    + ", players=" + mc.theWorld.playerEntities.size()
                    + ", enabled=" + enabledList() + ")");
        }

        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            viewer = mc.thePlayer;
        }

        // Interpolated camera position. RenderManager already computes exactly this and
        // exposes it publicly, so use its values rather than recomputing them slightly
        // differently here — a small mismatch shifts every box by a fraction and looks
        // like the feature is broken.
        float pt = event.partialTicks;
        double camX = rm.viewerPosX;
        double camY = rm.viewerPosY;
        double camZ = rm.viewerPosZ;
        if (camX == 0.0D && camY == 0.0D && camZ == 0.0D) {
            // Not cached yet (first frame): fall back to the manual interpolation.
            camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
            camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
            camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;
        }

        RenderContext ctx = new RenderContext(camX, camY, camZ, pt,
                rm.playerViewY, rm.playerViewX);

        for (Feature f : FeatureRegistry.all()) {
            if (!FeatureRegistry.isEnabled(f.id())) {
                continue;
            }
            try {
                f.renderWorld(ctx);
            } catch (Throwable t) {
                // A throw mid-draw skips that draw's cleanup, and a leftover matrix or
                // texture state breaks every pixel drawn afterwards.
                System.err.println("[PreSight] world '" + f.id() + "' threw: " + t);
                Draw.forceSaneState();
            }
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
