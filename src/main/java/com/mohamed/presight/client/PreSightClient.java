package com.mohamed.presight.client;

import com.mohamed.presight.command.PreSightCommand;
import com.mohamed.presight.esp.PlayerEspFeature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.gear.GearViewFeature;
import com.mohamed.presight.keybind.Keybinds;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client wiring: registers the features, their keys, and the three per-frame passes.
 *
 * Two buses, not interchangeable — this is where input handling usually goes wrong:
 * client tick and input events are posted on FML's bus
 * ({@code FMLCommonHandler.instance().bus()}), while render events are posted on
 * Forge's ({@code MinecraftForge.EVENT_BUS}). Subscribing to the wrong one registers
 * cleanly, logs nothing, and never fires.
 *
 * Every listener here is a public top-level class. Forge resolves handlers by
 * reflection and a listener that fails to be discovered is silent, which makes "the
 * feature is on but nothing draws" indistinguishable from "the handler never ran".
 */
@SideOnly(Side.CLIENT)
public final class PreSightClient {

    private static final WorldRenderHandler WORLD = new WorldRenderHandler();
    private static final HudRenderHandler HUD = new HudRenderHandler();
    private static final KeyPoller KEYS = new KeyPoller();

    private PreSightClient() {
    }

    /** Registration only — safe to call from preInit. */
    public static void init() {
        FeatureRegistry.register(new PlayerEspFeature());
        FeatureRegistry.register(new GearViewFeature());
        Keybinds.registerAll();
        // After registration: the stored state and keys are applied onto the features
        // and bindings that now exist.
        com.mohamed.presight.config.ConfigStore.load();
        System.out.println("[PreSight] features: " + Keybinds.describe());
    }

    /** Event bus subscriptions — after the buses exist. */
    public static void registerHandlers() {
        MinecraftForge.EVENT_BUS.register(WORLD);     // RenderWorldLastEvent
        MinecraftForge.EVENT_BUS.register(HUD);       // RenderGameOverlayEvent.Post
        FMLCommonHandler.instance().bus().register(KEYS);  // ClientTickEvent
        ClientCommandHandler.instance.registerCommand(new PreSightCommand());
    }

    /** Polls the toggle keys once per client tick. */
    @SideOnly(Side.CLIENT)
    public static final class KeyPoller {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;   // once per tick, not twice
            }
            try {
                Keybinds.poll();
            } catch (Throwable t) {
                // A dead key must not take down the game loop.
                System.err.println("[PreSight] key poll failed: " + t);
            }
        }
    }
}
