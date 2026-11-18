package com.mohamed.presight.gear;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player is visibly carrying.
 *
 * This is the honest limit of what a client can know: the server sends what a
 * player is holding and what they are wearing, nothing else. Their 36 inventory
 * slots are never transmitted, so this reads exactly those slots and no more —
 * inventing empty slots would be a lie the user would notice the moment they
 * compared it to the real player.
 */
public final class GearSnapshot {

    public final String playerName;
    public final ItemStack held;
    public final ItemStack[] armour;   // [0]=boots .. [3]=helmet, as getCurrentArmor indexes

    private GearSnapshot(String playerName, ItemStack held, ItemStack[] armour) {
        this.playerName = playerName;
        this.held = held;
        this.armour = armour;
    }

    /** Read the visible gear of a player. Never null; fields may be. */
    public static GearSnapshot of(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        ItemStack[] armour = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armour[i] = player.getCurrentArmor(i);
        }
        return new GearSnapshot(player.getName(), held, armour);
    }

    /** Everything present, helmet first, held item last. Used for rendering. */
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>(5);
        for (int i = 3; i >= 0; i--) {
            if (armour[i] != null) {
                out.add(armour[i]);
            }
        }
        if (held != null) {
            out.add(held);
        }
        return out;
    }

    public boolean isEmpty() {
        return presentItems().isEmpty();
    }
}
