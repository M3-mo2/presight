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
 * ({@code FMLCommonHandler.instance().bus()}), while render events are posted on Forge's
 * ({@code MinecraftForge.EVENT_BUS}). Subscribing to the wrong one registers cleanly,
 * logs nothing, and never fires.
 *
 * Every listener is a public top-level class: Forge resolves handlers by reflection and
 * a listener that fails to be discovered is silent, which makes "the feature is on but
 * nothing draws" indistinguishable from "the handler never ran".
 *
 * Each registration is separately guarded. They used to share one try block, and a single
 * NoSuchMethodError from the command registration took every render handler with it —
 * the mod loaded, the keys worked, and nothing drew.
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
        // Registered here and nowhere else: the keybinding, the config entry and the tick
        // loop all read the registry, so a new tool is one line.
        FeatureRegistry.register(new com.mohamed.presight.safety.BuildAssistantFeature());
        Keybinds.registerAll();
        // After registration: the stored state and keys are applied onto the features and
        // bindings that now exist.
        com.mohamed.presight.config.ConfigStore.load();
        System.out.println("[PreSight] features: " + Keybinds.describe());
    }

    /** Event bus subscriptions — after the buses exist. */
    public static void registerHandlers() {
        // Independent guards: a failure in one must not prevent the others, because a
        // missing render handler is invisible while a missing command is obvious.
        try {
            MinecraftForge.EVENT_BUS.register(WORLD);          // RenderWorldLastEvent
            System.out.println("[PreSight] world handler registered");
        } catch (Throwable t) {
            System.err.println("[PreSight] could not register the world handler: " + t);
            t.printStackTrace();
        }

        try {
            MinecraftForge.EVENT_BUS.register(HUD);            // RenderGameOverlayEvent.Post
            System.out.println("[PreSight] HUD handler registered");
        } catch (Throwable t) {
            System.err.println("[PreSight] could not register the HUD handler: " + t);
            t.printStackTrace();
        }

        try {
            FMLCommonHandler.instance().bus().register(KEYS);  // ClientTickEvent
            System.out.println("[PreSight] key poller registered");
        } catch (Throwable t) {
            System.err.println("[PreSight] could not register the key poller: " + t);
            t.printStackTrace();
        }

        try {
            // Cast to CommandHandler on purpose. registerCommand is declared on
            // CommandHandler and only inherited by ClientCommandHandler, and the SRG
            // remap is keyed on the declaring class: a call whose owner is
            // ClientCommandHandler is not in the map and survives as the readable name,
            // which then throws NoSuchMethodError at runtime. Naming the superclass as the
            // owner puts the call back on a key the map knows.
            net.minecraft.command.CommandHandler handler =
                    (net.minecraft.command.CommandHandler) ClientCommandHandler.instance;
            handler.registerCommand(new PreSightCommand());
            System.out.println("[PreSight] /presight registered");
        } catch (Throwable t) {
            // The command is a convenience; the features do not depend on it.
            System.err.println("[PreSight] /presight unavailable: " + t);
        }
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
            try {
                // Same tick, same bus: the tool that reacts to footing must run at tick rate,
                // not frame rate, or it decides sixty times a second and reacts late once.
                com.mohamed.presight.core.TickContext ctx =
                        com.mohamed.presight.core.TickContext.current();
                if (ctx != null) {
                    FeatureRegistry.tickAll(ctx);
                }
            } catch (Throwable t) {
                System.err.println("[PreSight] feature tick failed: " + t);
            }
        }
    }
}
