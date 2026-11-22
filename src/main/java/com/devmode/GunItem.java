package com.devmode;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Random;

/** Hitscan gun. Pistol / shotgun / sniper are the same class with different numbers. No ammo. */
public class GunItem extends Item {
    private static final Random RNG = new Random();
    private final float damage; private final int pellets; private final double spread, range; private final int cooldown;

    public GunItem(Settings s, float damage, int pellets, double spread, double range, int cooldown) {
        super(s);
        this.damage = damage; this.pellets = pellets; this.spread = spread; this.range = range; this.cooldown = cooldown;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && world instanceof ServerWorld sw) {
            for (int i = 0; i < pellets; i++) fire(sw, user);
            sw.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 2f, pellets > 1 ? 0.6f : 1.2f);
            user.getItemCooldownManager().set(this, cooldown);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    private void fire(ServerWorld w, PlayerEntity user) {
        Vec3d start = user.getEyePos();
        Vec3d dir = user.getRotationVec(1f)
                .add(RNG.nextGaussian() * spread, RNG.nextGaussian() * spread, RNG.nextGaussian() * spread).normalize();
        Vec3d end = start.add(dir.multiply(range));
        BlockHitResult bh = w.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        Vec3d limit = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();

        EntityHitResult eh = ProjectileUtil.raycast(user, start, limit, new Box(start, limit).expand(1.0),
                e -> !e.isSpectator() && e.canHit(), range * range);
        if (eh != null) {
            Entity t = eh.getEntity();
            if (t instanceof LivingEntity le) le.timeUntilRegen = 0; // lets every shotgun pellet land
            t.damage(w.getDamageSources().playerAttack(user), damage);
            limit = eh.getPos();
        }
        double len = start.distanceTo(limit);
        for (double d = 1.5; d < len; d += 1.0) {
            Vec3d p = start.add(dir.multiply(d));
            w.spawnParticles(ParticleTypes.END_ROD, p.x, p.y - 0.1, p.z, 1, 0, 0, 0, 0);
        }
    }
}
