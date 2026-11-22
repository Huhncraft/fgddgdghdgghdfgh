package com.devmode;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Right-click to throw. */
public class PocketKnifeItem extends Item {
    public PocketKnifeItem(Settings s) { super(s); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.8f, 1.8f);
        if (!world.isClient) {
            PocketKnifeEntity knife = new PocketKnifeEntity(world, user);
            knife.setItem(stack);
            knife.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 2.8f, 0.3f);
            world.spawnEntity(knife);
        }
        user.getItemCooldownManager().set(this, 8);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient);
    }
}
