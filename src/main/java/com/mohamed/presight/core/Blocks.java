package com.mohamed.presight.core;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * The small set of questions every safety tool has to ask, answered in one place.
 *
 * The point is not brevity, it is agreement: two features that each decide "is that block
 * solid" slightly differently will disagree at the edges, and the player sees one of them
 * as broken. Everything here is world state only — nothing renders, nothing mutates.
 */
public final class Blocks {

    private Blocks() {
    }

    /** Air, tall grass, a liquid, a torch — anything you can move into. */
    public static boolean passable(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (world.isAirBlock(pos)) {
            return true;
        }
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        if (block == null) {
            return true;
        }
        net.minecraft.block.material.Material material = block.getMaterial();
        if (material == null) {
            return false;
        }
        // Liquids are not "empty" — falling into water is survivable, falling into lava is
        // not, and the player asked about a void, so they are treated as something to land
        // on only for the purpose of not adding a block.
        return material.isReplaceable();
    }

    /** Something with collision you can stand on. */
    public static boolean standable(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (world.isAirBlock(pos)) {
            return false;
        }
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        if (block == null) {
            return false;
        }
        net.minecraft.block.material.Material material = block.getMaterial();
        if (material == null) {
            return false;
        }
        return material.isSolid() && !material.isLiquid();
    }

    /** Anything at or below this world height is the void on a normal overworld. */
    public static boolean isVoid(World world, double y) {
        return y < -1.0D;
    }

    /** How far the drop under a player's feet is, in blocks, before solid ground. */
    public static int dropBelow(World world, EntityPlayer player, int maxScan) {
        if (world == null || player == null) {
            return maxScan;
        }
        int x = net.minecraft.util.MathHelper.floor_double(player.posX);
        int z = net.minecraft.util.MathHelper.floor_double(player.posZ);
        int feet = net.minecraft.util.MathHelper.floor_double(player.posY);
        for (int d = 1; d <= maxScan; d++) {
            BlockPos pos = new BlockPos(x, feet - d, z);
            if (standable(world, pos)) {
                return d - 1;
            }
            // getY is declared on Vec3i, which BlockPos extends. Calling it with BlockPos as
            // the owner leaves a name the SRG map does not have, and that is a
            // NoSuchMethodError at runtime — the exact trap that killed registerCommand.
            if (isVoid(world, ((net.minecraft.util.Vec3i) pos).getY())) {
                return maxScan;
            }
        }
        return maxScan;
    }

    /** Convenience: the block position the player is standing in. */
    public static BlockPos feet(EntityPlayer player) {
        return new BlockPos(
                net.minecraft.util.MathHelper.floor_double(player.posX),
                net.minecraft.util.MathHelper.floor_double(player.posY),
                net.minecraft.util.MathHelper.floor_double(player.posZ));
    }

    /** The client, or null when the game is not in a state to be asked anything. */
    public static Minecraft mc() {
        return Minecraft.getMinecraft();
    }
}
