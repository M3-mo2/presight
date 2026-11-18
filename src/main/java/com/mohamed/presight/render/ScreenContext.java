package com.mohamed.presight.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;

/**
 * Screen-space drawing for HUD overlays.
 *
 * Wraps Gui's static helpers so features never have to extend Gui or hold a
 * GuiScreen reference — they just say "draw this at that pixel".
 */
public final class ScreenContext {

    public final int width;
    public final int height;
    public final FontRenderer font;

    public ScreenContext(int width, int height, FontRenderer font) {
        this.width = width;
        this.height = height;
        this.font = font;
    }

    public void rect(int x, int y, int w, int h, int argb) {
        float a = (argb >>> 24 & 255) / 255.0F;
        Gui.drawRect(x, y, x + w, y + h, toArgb(a, argb));
    }

    public void outline(int x, int y, int w, int h, int argb) {
        Gui.drawRect(x, y, x + w, y + 1, argb);
        Gui.drawRect(x, y + h - 1, x + w, y + h, argb);
        Gui.drawRect(x, y, x + 1, y + h, argb);
        Gui.drawRect(x + w - 1, y, x + w, y + h, argb);
    }

    public void text(String s, int x, int y, int colour) {
        font.drawStringWithShadow(s, x, y, colour);
    }

    public void textCentred(String s, int cx, int y, int colour) {
        font.drawStringWithShadow(s, cx - font.getStringWidth(s) / 2, y, colour);
    }

    public int textWidth(String s) {
        return font.getStringWidth(s);
    }

    /**
     * Draw an item exactly as the inventory does — icon plus the stack-count
     * overlay, which is the part people forget and then wonder why a stack of 64
     * looks like one item.
     */
    public void itemStack(net.minecraft.item.ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlayIntoGUI(font, stack, x, y, null);
    }

    private static int toArgb(float alpha, int argb) {
        int ai = (int) (alpha * 255.0F) << 24;
        return ai | (argb & 0x00FFFFFF);
    }
}
