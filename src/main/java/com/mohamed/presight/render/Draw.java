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
 * The state discipline here is the whole point. In 1.8.9 one leftover GL state breaks
 * everything drawn after it — texture sampling left off turns the entire interface
 * white, a leaked depth mask hides the world, a stuck colour tints all text. And an
 * exception thrown mid-draw leaves exactly that, because the cleanup never runs.
 *
 * So every method restores what it changed, and {@link #forceSaneState()} exists for
 * the case where a draw threw and the normal restore was skipped.
 *
 * Screen and world drawing are strictly separated. A rect drawn while the 3D
 * perspective matrix is still bound is not a small overlay — it is a giant quad out in
 * the world, which is what "the screen went white" actually was.
 */
public final class Draw {

    /**
     * How many matrices this mod has pushed and not yet popped.
     *
     * The legacy stack is 32 deep. A draw that throws between its push and its pop
     * leaves one behind permanently, every frame adds another, and after 32 frames the
     * driver reports GL_STACK_OVERFLOW (1283) — an error whose message says nothing
     * about the code that caused it. Counting them here is what makes it recoverable.
     */
    private static int depth;

    private Draw() {
    }

    /** Push a matrix, remembered so it can be unwound if the draw throws. */
    private static void push() {
        push();
        depth++;
    }

    /** Pop a matrix, but never below the level we started at. */
    private static void pop() {
        if (depth <= 0) {
            return;
        }
        pop();
        depth--;
    }

    // ---------------------------------------------------------------- world space

    /** A line box between two corners, in camera-relative coordinates. */
    public static void box(double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           float r, float g, float b, float a, float width) {
        push();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glLineWidth(width);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // 12 edges, written out: corner order is the part that is easy to get wrong and
        // hard to spot once you have.
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
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        pop();
    }

    private static void edge(WorldRenderer wr, double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    /** Text in world space, always facing the camera. Coordinates are camera-relative. */
    public static void text3d(String text, double x, double y, double z, float scale,
                              int colour, RenderContext ctx) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        push();
        GlStateManager.translate(x, y, z);
        // Billboard on both axes, or the text lies flat on the ground and is unreadable
        // unless you happen to be directly above it.
        GlStateManager.rotate(-ctx.viewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(ctx.viewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int width = font.getStringWidth(text);
        font.drawStringWithShadow(text, -width / 2, 0, colour);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        pop();
    }

    // ------------------------------------------------------------- screen space

    /**
     * Prepares an orthographic overlay.
     *
     * Called from the HUD pass, where Minecraft has already set up the 2D projection, so
     * this only has to fix the states the drawing below will disturb.
     */
    public static void beginGui() {
        push();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
    }

    public static void endGui() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        pop();
    }

    /** A filled rectangle in scaled-screen pixels. Call inside beginGui/endGui. */
    public static void rect(float x, float y, float w, float h, int argb) {
        float a = (argb >>> 24 & 255) / 255.0F;
        float r = (argb >> 16 & 255) / 255.0F;
        float g = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r, g, b, a).endVertex();
        wr.pos(x + w, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y, 0).color(r, g, b, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A one-pixel border: four rects, so it is correct at any size. */
    public static void outline(float x, float y, float w, float h, int argb) {
        rect(x, y, w, 1, argb);
        rect(x, y + h - 1, w, 1, argb);
        rect(x, y, 1, h, argb);
        rect(x + w - 1, y, 1, h, argb);
    }

    /**
     * Hard reset to the state the vanilla HUD expects.
     *
     * Called after a caught exception: a throw in the middle of a draw skips its own
     * cleanup, and the result is the entire interface rendering white — which looks like
     * a graphics driver fault rather than a mod fault.
     */
    public static void forceSaneState() {
        try {
            // Unwind first: a draw that threw skipped its own pop, and leaving the stack
            // deeper than it started makes the overflow permanent and progressive.
            while (depth > 0) {
                GlStateManager.popMatrix();
                depth--;
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.disableFog();
            GL11.glLineWidth(1.0F);
        } catch (Throwable ignored) {
            // Nothing left to try; the next frame redraws from scratch.
        }
    }
}
