package com.mohamed.presight.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Queues world-space text and draws it after all lines are done.
 *
 * This exists because of GL_INVALID_OPERATION (1282). Text has to change GL state —
 * enable texturing, bind a different blend, push a matrix — and every one of those calls
 * is illegal between glBegin and glEnd. Drawing text while the box batch is open produced
 * exactly that error, on every frame, for every player.
 *
 * So text is collected during the pass and drawn once, after the batch has closed.
 */
public final class TextBatch {

    private static final int MAX_ENTRIES = 64;

    private static final class Entry {
        final String text;
        final double x, y, z;
        final float scale;
        final int colour;

        Entry(String text, double x, double y, double z, float scale, int colour) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.colour = colour;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Queue a label. Silently drops extras: a wall of text is neither readable nor fast. */
    public void add(String text, double x, double y, double z, float scale, int colour) {
        if (text == null || text.isEmpty() || entries.size() >= MAX_ENTRIES) {
            return;
        }
        entries.add(new Entry(text, x, y, z, scale, colour));
    }

    public int size() {
        return entries.size();
    }

    /** Draw everything queued and clear the queue. */
    public void draw(RenderContext ctx) {
        if (entries.isEmpty()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        if (font != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);

            for (Entry e : entries) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(e.x, e.y, e.z);
                // Billboard on both axes, or the text lies flat on the ground and cannot
                // be read unless you are directly above it.
                GlStateManager.rotate(-ctx.viewY, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(ctx.viewX, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(-e.scale, -e.scale, e.scale);
                int w = font.getStringWidth(e.text);
                font.drawStringWithShadow(e.text, -w / 2, 0, e.colour);
                GlStateManager.popMatrix();
            }

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
        }
        entries.clear();
    }
}
