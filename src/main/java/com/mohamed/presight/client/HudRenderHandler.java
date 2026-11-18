package com.mohamed.presight.client;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.ScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The HUD pass, plus a status line that exists to be seen.
 *
 * The line is deliberate. From inside the game, "nothing draws" and "this handler never
 * ran" look identical, and they need opposite fixes. One visible row — did the pass run,
 * how many players are in the world, which features are on — turns that into a glance.
 *
 * Public top-level class: Forge discovers handlers by reflection, and a listener that
 * fails to be found registers cleanly and then says nothing at all.
 */
public class HudRenderHandler {

    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFAAAAAA;

    private boolean announced;
    private int frameCount;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.fontRendererObj == null) {
            return;
        }

        frameCount++;
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
                // A throw mid-draw skips its cleanup, and the leftover state is what turns
                // the whole interface white.
                System.err.println("[PreSight] screen '" + f.id() + "' threw:");
                t.printStackTrace();
                Draw.forceSaneState();
            }
        }

        if (anyEnabled()) {
            try {
                drawStatus(ctx, mc);
            } catch (Throwable t) {
                System.err.println("[PreSight] status line threw:");
                t.printStackTrace();
                Draw.forceSaneState();
            }
        }
    }

    /**
     * One row at the top-left. If this is visible, the HUD pass is alive and any missing
     * feature is a coordinate problem rather than a wiring problem — which is the
     * distinction that has been missing.
     */
    private void drawStatus(ScreenContext ctx, Minecraft mc) {
        int others = 0;
        for (Object o : mc.theWorld.playerEntities) {
            if (o != mc.thePlayer) {
                others++;
            }
        }

        String line = "PreSight " + com.mohamed.presight.PreSight.VERSION
                + " | players " + others
                + " | " + enabledList();

        // Also to the log, periodically, so it can be sent without a screenshot.
        if (frameCount % 200 == 0) {
            System.out.println("[PreSight] status: " + line);
        }

        FontRenderer font = ctx.font;
        int w = font.getStringWidth(line);
        int x = 4;
        int y = 4;

        Draw.beginGui();
        try {
            Draw.rect(x - 2, y - 2, w + 6, 12, 0xC0000000);
            Draw.outline(x - 2, y - 2, w + 6, 12, others > 0 ? GREEN : RED);
            // Raw font call: the simplest thing that definitely works, so its visibility
            // says something unambiguous about the pass itself.
            font.drawStringWithShadow(line, x + 1, y + 1, others > 0 ? GREEN : GREY);
        } finally {
            Draw.endGui();
        }
    }

    private static boolean anyEnabled() {
        for (Feature f : FeatureRegistry.all()) {
            if (FeatureRegistry.isEnabled(f.id())) {
                return true;
            }
        }
        return false;
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
        return sb.toString();
    }
}
