package com.mohamed.presight.click;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Mouse;

/**
 * Reads mouse presses as they happen, instead of polling for them.
 *
 * <h3>Why this exists</h3>
 *
 * The multiplier used to check {@code Mouse.isButtonDown(0)} once per client tick. That is a
 * level test, and it misses exactly what a click multiplier needs: LWJGL queues button events
 * and drains them during the game's own input pass, so by the time the tick hook runs the flag
 * is already back up. The tool reported ON and saw no presses at all.
 *
 * {@code MouseInputEvent} is posted on FML's bus at the moment the event is drained, and
 * {@code Mouse.getEventButton()} / {@code getEventButtonState()} describe that specific event.
 * So a press is a press, not a level that may already have passed.
 *
 * Registered on FML's bus, not Forge's: input events are on {@code FMLCommonHandler.instance()
 * .bus()}. Registering this on {@code MinecraftForge.EVENT_BUS} succeeds silently and never
 * fires.
 */
public class MouseInputHandler {

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        // The event carries no payload: the data is read from LWJGL for the event currently
        // being drained. -1 means "no button event", which is the common case.
        int button = Mouse.getEventButton();
        if (button < 0) {
            return;
        }
        boolean down = Mouse.getEventButtonState();

        ClickMultiplierFeature cm = multiplier();
        if (cm == null) {
            return;
        }

        // Our injected clicks come back as a press AND a release each, so both are swallowed
        // here, before anything else looks at them. Charging only the presses let the releases
        // through, which reset the count and made 5 real clicks produce 12 bursts.
        if (cm.consumeInjected()) {
            return;
        }

        if (!down) {
            return;   // only presses cause a burst
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) {
            // A press while a screen is open belongs to that screen, not the world.
            return;
        }

        // The feature's own state, not the registry's.
        //
        // I opens the control screen without toggling the registry entry — the screen is for
        // configuring, and toggling it there would arm the tool just by looking at it. Gating
        // on FeatureRegistry.isEnabled() therefore skipped every press.
        if (!cm.active()) {
            return;
        }

        try {
            cm.onMousePress(button);
        } catch (Throwable t) {
            // A failed burst must not take the player's own click down with it.
            System.err.println("[PreSight] click_multiplier press failed: " + t);
        }
    }

    private static ClickMultiplierFeature multiplier() {
        for (Feature f : FeatureRegistry.all()) {
            if (f instanceof ClickMultiplierFeature) {
                return (ClickMultiplierFeature) f;
            }
        }
        return null;
    }
}
