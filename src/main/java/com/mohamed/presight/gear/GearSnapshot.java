package com.mohamed.presight.gear;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player is visibly carrying.
 *
 * The honest limit of what a client knows: the server sends what a player is wearing and
 * what they are holding, nothing else. Their 36 inventory slots are never transmitted, so
 * this reads exactly those slots — inventing empty ones would be a lie the user notices
 * the moment they compare it to the real player.
 *
 * The held item is read through getEquipmentInSlot(0), not getHeldItem(). Both resolve to
 * the same array for your own player, but for another player the server only synchronises
 * the equipment array — index 0 is what they are holding, while their inventory cursor
 * (currentItem) is local state you never receive. Reading getHeldItem() for someone else
 * therefore returns whatever their inventory slot happens to point at on your client,
 * which is usually nothing.
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
        // Index 0 of the equipment array is the item in hand, and it is the one the
        // server actually sends for other players.
        ItemStack held = player.getEquipmentInSlot(0);

        ItemStack[] armour = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armour[i] = player.getCurrentArmor(i);
        }
        return new GearSnapshot(player.getName(), held, armour);
    }

    /** Everything present: the held item first, then helmet down to boots. */
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>(5);
        if (held != null) {
            out.add(held);
        }
        for (int i = 3; i >= 0; i--) {
            if (armour[i] != null) {
                out.add(armour[i]);
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return presentItems().isEmpty();
    }
}
