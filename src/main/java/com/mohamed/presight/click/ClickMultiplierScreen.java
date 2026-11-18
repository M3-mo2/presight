package com.mohamed.presight.click;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * The small control screen: which button to multiply, and by how much.
 *
 * Vanilla {@link GuiScreen} with vanilla {@link GuiButton}s, because in 1.8 those are the
 * only widgets guaranteed to exist with a stable API across 1.8 and 1.8.9 — a hand-drawn
 * screen would need input handling and a font pass that the game already does correctly.
 *
 * The screen never opens the tool by itself: picking a value does. Opening it to look is safe.
 */
public class ClickMultiplierScreen extends GuiScreen {

    private static final int ID_LEFT = 1;
    private static final int ID_RIGHT = 2;
    private static final int ID_COUNT_BASE = 100;   // +2..+5
    private static final int ID_DONE = 90;
    private static final int ID_OFF = 91;

    private final ClickMultiplierFeature feature;

    public ClickMultiplierScreen(ClickMultiplierFeature feature) {
        this.feature = feature;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int y = height / 2 - 40;

        buttonList.add(new GuiButton(ID_LEFT, cx - 155, y, 150, 20,
                "Left click" + (feature.button() == ClickMultiplierFeature.Button.LEFT ? "  <" : "")));
        buttonList.add(new GuiButton(ID_RIGHT, cx + 5, y, 150, 20,
                "Right click" + (feature.button() == ClickMultiplierFeature.Button.RIGHT ? "  <" : "")));

        // Four choices, laid out as one row: 2, 3, 4, 5.
        int bw = 72;
        int total = bw * 4 + 3 * 4;
        int x = cx - total / 2;
        for (int n = 2; n <= 5; n++) {
            String label = "x" + n + (feature.count() == n ? "  <" : "");
            buttonList.add(new GuiButton(ID_COUNT_BASE + n, x, y + 34, bw, 20, label));
            x += bw + 4;
        }

        buttonList.add(new GuiButton(ID_OFF, cx - 155, y + 74, 150, 20, "Turn off"));
        buttonList.add(new GuiButton(ID_DONE, cx + 5, y + 74, 150, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        switch (button.id) {
            case ID_LEFT:
                feature.configure(ClickMultiplierFeature.Button.LEFT, feature.count());
                initGui();
                return;
            case ID_RIGHT:
                feature.configure(ClickMultiplierFeature.Button.RIGHT, feature.count());
                initGui();
                return;
            case ID_OFF:
                feature.configure(feature.button(), feature.count());
                // configure() arms; turning off is the explicit path.
                feature.onKeyPressed(true);
                mc.displayGuiScreen(null);
                return;
            case ID_DONE:
                mc.displayGuiScreen(null);
                return;
            default:
                break;
        }
        if (button.id > ID_COUNT_BASE) {
            int n = button.id - ID_COUNT_BASE;
            feature.configure(feature.button(), n);
            initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // The world keeps drawing behind us; the panel only dims what is under it, so the
        // player can still see where they are aiming while choosing.
        drawDefaultBackground();
        drawCentred("Click Multiplier", width / 2, height / 2 - 68, 0xFFFFFF);
        drawCentred("Multiply " + feature.describe(), width / 2, height / 2 - 54, 0x9BFF6B);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCentred("Shift + I turns it off", width / 2, height / 2 + 46, 0x808080);
    }

    private void drawCentred(String text, int cx, int y, int colour) {
        fontRendererObj.drawStringWithShadow(text, cx - fontRendererObj.getStringWidth(text) / 2,
                y, colour);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // False on purpose: this is a HUD-adjacent control panel, not a menu. Pausing would
        // also stop the tick loop, and the tick loop is what applies the setting.
        return false;
    }
}
