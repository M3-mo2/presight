package com.mohamed.presight.esp;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * A bright box around every other player, at any distance, through walls.
 *
 * The box is the entity's own bounding box, so a crouching player gets a crouching box
 * and a player on a horse gets a tall one — no guessing at a fixed size.
 */
public class PlayerEspFeature implements Feature {

    public static final String ID = "player_esp";

    private static final float R = 0.15F;
    private static final float G = 1.0F;
    private static final float B = 0.55F;

    /** Logged once so "no box on screen" can be told apart from "no players found". */
    private boolean reported;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Player ESP";
    }

    @Override
    public int defaultKey() {
        return Keyboard.KEY_G;
    }

    @Override
    public void renderWorld(RenderContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        int drawn = 0;
        // playerEntities is the client's list of other players; it is populated on a
        // server too (that is what the tab list and the player models come from), so
        // this is not a single-player-only path.
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == null || p == mc.thePlayer) {
                continue;
            }

            double half = p.width / 2.0D;
            double cx = ctx.relX(p);
            double cz = ctx.relZ(p);
            double y1 = ctx.relY(p);
            double y2 = y1 + p.height;

            Draw.box(cx - half, y1, cz - half, cx + half, y2, cz + half,
                    R, G, B, 1.0F, 2.0F);
            Draw.text3d(p.getName(), cx, y2 + 0.45D, cz, 0.03F, 0xFF9BFF6B, ctx);
            drawn++;
        }

        if (!reported) {
            reported = true;
            System.out.println("[PreSight] ESP: " + drawn + " other player(s) in world,"
                    + " camera=(" + fmt(ctx.camX) + "," + fmt(ctx.camY) + "," + fmt(ctx.camZ) + ")");
        }
    }

    private static String fmt(double d) {
        return String.format("%.1f", d);
    }
}
