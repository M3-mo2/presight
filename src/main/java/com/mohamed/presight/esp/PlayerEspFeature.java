package com.mohamed.presight.esp;

import com.mohamed.presight.feature.Feature;
import com.mohamed.presight.render.Draw;
import com.mohamed.presight.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * A bright box around every other player, at any distance.
 *
 * The box is the entity's own bounding box, so a crouching player gets a crouching
 * box and a player on a horse gets a tall one — no guessing at a fixed size.
 */
public class PlayerEspFeature implements Feature {

    public static final String ID = "player_esp";

    /** Drawn above the head as well as a box, so a player is findable at range. */
    private static final float R = 0.15F;
    private static final float G = 1.0F;
    private static final float B = 0.55F;

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

        for (Entity e : mc.theWorld.playerEntities) {
            if (!(e instanceof EntityPlayer) || e == mc.thePlayer) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) e;

            // Bounding box in world-relative coordinates. width/height come from the
            // entity, so armour, crouching and riding are all handled for free.
            double half = p.width / 2.0D;
            double x1 = ctx.relX(p) - half;
            double x2 = ctx.relX(p) + half;
            double z1 = ctx.relZ(p) - half;
            double z2 = ctx.relZ(p) + half;
            double y1 = ctx.relY(p);
            double y2 = ctx.relY(p) + p.height;

            Draw.box(x1, y1, z1, x2, y2, z2, R, G, B, 1.0F, 2.0F);

            String name = p.getName();
            Draw.text3d(name, ctx.relX(p), y2 + 0.4D, ctx.relZ(p), 0.03F, 0xFF9BFF6B, ctx);
        }
    }
}
