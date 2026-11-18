package com.mohamed.presight.client;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.ScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The HUD pass — the only place screen-space drawing is correct, because Minecraft
 * has already set the orthographic projection here.
 *
 * Public top-level class, same reason as WorldRenderHandler: handler discovery is by
 * reflection, and a private nested listener is one more thing that can register
 * silently and never be called.
 */
public class HudRenderHandler {

    private boolean announced;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.fontRendererObj == null) {
            return;
        }

        if (!announced) {
            announced = true;
            System.out.println("[PreSight] HUD pass is live");
        }

        ScreenContext ctx = new ScreenContext(
                event.resolution.getScaledWidth(),
                event.resolution.getScaledHeight(),
                mc.fontRendererObj);

        for (Feature f : FeatureRegistry.all()) {
            if (!FeatureRegistry.isEnabled(f.id())) {
                continue;
            }
            try {
                f.renderScreen(ctx);
            } catch (Throwable t) {
                // A throw mid-draw skips its cleanup, and the leftover state is what
                // turns the whole interface white.
                System.err.println("[PreSight] screen '" + f.id() + "' threw: " + t);
                Draw.forceSaneState();
            }
        }
    }
}
