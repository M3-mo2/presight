package com.mohamed.presight.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;

/**
 * Screen-space drawing for HUD overlays.
 *
 * Every rect goes through {@link Gui#drawRect}, which is vanilla code and therefore
 * correct for whichever Minecraft is running. Hand-rolled immediate-mode quads were the
 * cause of GL_INVALID_OPERATION (1282): mixing glBegin/glEnd with a Tessellator that the
 * item renderer and the font renderer use in the same pass leaves the driver in a state
 * where the next primitive is illegal.
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
        Gui.drawRect(x, y, x + w, y + h, argb);
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
     * An item exactly as the inventory draws it — icon plus the stack-count overlay,
     * which is the part people forget and then wonder why a stack of 64 looks like one.
     */
    public void itemStack(net.minecraft.item.ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlayIntoGUI(font, stack, x, y, null);
    }
}
