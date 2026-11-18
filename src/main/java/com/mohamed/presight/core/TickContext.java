package com.mohamed.presight.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;

/**
 * The state a per-tick feature is allowed to see.
 *
 * Same idea as RenderContext: the frame-rate pass and the tick-rate pass must agree on
 * which player and which world they are talking about, and passing it around stops each
 * feature from calling Minecraft.getMinecraft() and quietly disagreeing.
 */
public class TickContext {

    public final Minecraft mc;
    public final EntityPlayerSP player;
    public final World world;
    /** Client ticks since the world loaded; monotonic for the life of a session. */
    public final int ticks;

    public TickContext(Minecraft mc, EntityPlayerSP player, World world, int ticks) {
        this.mc = mc;
        this.player = player;
        this.world = world;
        this.ticks = ticks;
    }

    /** Build one from the live game, or null when nothing is loaded. */
    public static TickContext current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        return new TickContext(mc, mc.thePlayer, mc.theWorld, mc.thePlayer.ticksExisted);
    }
}
