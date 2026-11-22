package com.devmode;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Thrown knife: 6 hearts of damage, then drops as an item where it lands. */
public class PocketKnifeEntity extends ThrownItemEntity {
    public PocketKnifeEntity(EntityType<? extends PocketKnifeEntity> type, World world) { super(type, world); }
    public PocketKnifeEntity(World world, LivingEntity owner) { super(DevModeMod.POCKET_KNIFE_ENTITY, owner, world); }

    @Override protected Item getDefaultItem() { return DevModeMod.POCKET_KNIFE; }

    @Override
    protected void onEntityHit(EntityHitResult r) {
        super.onEntityHit(r);
        r.getEntity().damage(getDamageSources().thrown(this, getOwner()), 12f);
    }

    @Override
    protected void onCollision(HitResult r) {
        super.onCollision(r);
        if (!getWorld().isClient) {
            dropStack(new ItemStack(DevModeMod.POCKET_KNIFE));
            discard();
        }
    }
}
