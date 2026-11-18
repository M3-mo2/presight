package com.mohamed.presight.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Collects the frame's boxes and draws them.
 *
 * Uses fixed-function GL (glBegin/glVertex3d) rather than the Tessellator on purpose:
 * the Tessellator's vertex API changed between 1.8 and 1.8.9 (pos/color/endVertex were
 * added in 1.8.9), so a mod built against either version throws NoSuchMethodError on the
 * other. These calls are the same in every version of Minecraft since 1.0.
 *
 * The old path also submitted one flush per box, which is one per player per frame —
 * enough to halve the frame rate on a full server.
 */
public final class BoxBatch {

    private boolean open;
    private float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;

    /** Begin drawing. Safe to call twice; the second is ignored. */
    public void begin(float lineWidth) {
        if (open) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINES);
        open = true;
    }

    /** Set the colour for the edges added next. */
    public void colour(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    /** The 12 edges of an axis-aligned box, in camera-relative coordinates. */
    public void addBox(double x1, double y1, double z1,
                       double x2, double y2, double z2) {
        if (!open) {
            return;
        }
        edge(x1, y1, z1, x2, y1, z1);
        edge(x1, y1, z2, x2, y1, z2);
        edge(x1, y2, z1, x2, y2, z1);
        edge(x1, y2, z2, x2, y2, z2);

        edge(x1, y1, z1, x1, y2, z1);
        edge(x2, y1, z1, x2, y2, z1);
        edge(x1, y1, z2, x1, y2, z2);
        edge(x2, y1, z2, x2, y2, z2);

        edge(x1, y1, z1, x1, y1, z2);
        edge(x2, y1, z1, x2, y1, z2);
        edge(x1, y2, z1, x1, y2, z2);
        edge(x2, y2, z1, x2, y2, z2);
    }

    private void edge(double x1, double y1, double z1,
                      double x2, double y2, double z2) {
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    /** Finish the draw and restore every state this touched. */
    public void flush() {
        if (!open) {
            return;
        }
        GL11.glEnd();
        open = false;

        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
