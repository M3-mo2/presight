package com.mohamed.presight.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * The drawing primitives every feature shares.
 *
 * All of it is a thin, correct wrapper over GlStateManager + Tessellator: the
 * ordering of state changes is what breaks visuals in 1.8.9 (a leaked depth mask
 * makes the whole HUD vanish), so it is written once here rather than per feature.
 */
public final class Draw {

    private Draw() {
    }

    /** A line box between two corners, drawn in the current GL space. */
    public static void box(double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           float r, float g, float b, float a, float width) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GL11.glLineWidth(width);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // 12 edges. Written out rather than looped over a corner array: the corner
        // order is the thing that is easy to get wrong and hard to see.
        edge(wr, x1, y1, z1, x2, y1, z1, r, g, b, a);
        edge(wr, x1, y1, z2, x2, y1, z2, r, g, b, a);
        edge(wr, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(wr, x1, y2, z2, x2, y2, z2, r, g, b, a);

        edge(wr, x1, y1, z1, x1, y2, z1, r, g, b, a);
        edge(wr, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(wr, x1, y1, z2, x1, y2, z2, r, g, b, a);
        edge(wr, x2, y1, z2, x2, y2, z2, r, g, b, a);

        edge(wr, x1, y1, z1, x1, y1, z2, r, g, b, a);
        edge(wr, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(wr, x1, y2, z1, x1, y2, z2, r, g, b, a);
        edge(wr, x2, y2, z1, x2, y2, z2, r, g, b, a);

        tess.draw();

        GL11.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void edge(WorldRenderer wr, double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    /**
     * Text in world space, always facing the camera.
     *
     * The camera position must already have been subtracted from (x, y, z), and this
     * must be called inside a matrix pushed by the caller so a feature cannot leak
     * its transform into the next one.
     */
    public static void text3d(String text, double x, double y, double z, float scale,
                              int colour, RenderContext ctx) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        // Billboard on both axes, or the text lies flat on the ground and is
        // unreadable unless you are directly above it.
        GlStateManager.rotate(-ctx.viewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(ctx.viewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        int width = font.getStringWidth(text);
        font.drawStringWithShadow(text, -width / 2, 0, colour);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /** Set up an orthographic overlay pass in screen space. */
    public static void beginOverlay() {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
    }

    public static void endOverlay() {
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** A filled rectangle in screen coordinates. */
    public static void rect(int x, int y, int w, int h, int argb) {
        float a = (argb >>> 24 & 255) / 255.0F;
        float r = (argb >> 16 & 255) / 255.0F;
        float g = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y, 0).color(r, g, b, a).endVertex();
        tess.draw();
    }

    /** Thin border, four rectangles so it works at any size. */
    public static void outline(int x, int y, int w, int h, int argb) {
        rect(x, y, w, 1, argb);
        rect(x, y + h - 1, w, 1, argb);
        rect(x, y, 1, h, argb);
        rect(x + w - 1, y, 1, h, argb);
    }
}
