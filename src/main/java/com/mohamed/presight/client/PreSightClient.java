package com.mohamed.presight.client;

import com.mohamed.presight.command.PreSightCommand;
import com.mohamed.presight.esp.PlayerEspFeature;
import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.feature.FeatureRegistry;
import com.mohamed.presight.gear.GearViewFeature;
import com.mohamed.presight.keybind.Keybinds;
import com.mohamed.presight.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client wiring: registers the features, their keys, and the two per-frame passes.
 *
 * Two buses are involved and they are not interchangeable, which is where key
 * handling goes wrong: client input and tick events are posted on FML's bus
 * ({@code FMLCommonHandler.instance().bus()}), while world render events are posted on
 * Forge's ({@code MinecraftForge.EVENT_BUS}). Subscribing to the wrong one registers
 * cleanly, logs nothing, and never fires.
 */
@SideOnly(Side.CLIENT)
public final class PreSightClient {

    private static final WorldRenderer RENDERER = new WorldRenderer();
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
        // World render: Forge's bus.
        MinecraftForge.EVENT_BUS.register(RENDERER);
        // Client tick: FML's bus. This is what drives the key polling.
        FMLCommonHandler.instance().bus().register(KEYS);
        ClientCommandHandler.instance.registerCommand(new PreSightCommand());
    }

    /** Polls the toggle keys once per client tick. */
    @SideOnly(Side.CLIENT)
    private static final class KeyPoller {

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

    /** Turns the frame into a RenderContext and hands it to every enabled feature. */
    @SideOnly(Side.CLIENT)
    private static final class WorldRenderer {

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.fontRendererObj == null) {
                return;
            }

            RenderManager rm = mc.getRenderManager();
            if (rm == null) {
                return;
            }

            Entity viewer = mc.getRenderViewEntity();
            if (viewer == null) {
                viewer = mc.thePlayer;
            }

            // Interpolated camera position: raw posX makes boxes jitter against a
            // smoothly moving world at low frame rates.
            float pt = event.partialTicks;
            double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
            double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
            double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

            RenderContext ctx = new RenderContext(camX, camY, camZ, pt,
                    rm.playerViewY, rm.playerViewX);

            for (Feature f : FeatureRegistry.all()) {
                if (FeatureRegistry.isEnabled(f.id())) {
                    try {
                        f.renderWorld(ctx);
                    } catch (RuntimeException e) {
                        // One broken feature must not take the frame down with it, or
                        // the game becomes unplayable and the cause is invisible.
                        System.err.println("[PreSight] feature '" + f.id() + "' threw: " + e);
                    }
                }
            }
        }
    }
}
