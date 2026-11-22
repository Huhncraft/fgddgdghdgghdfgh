package com.devmode;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;


/**
 * Sword of Revenge.
 *  - every hit drops the target to half a heart (1 HP)
 *  - extremely long: left-click reaches 14 blocks (client sends a packet, see MovementController)
 *  - right-click: Hollow Purple      - sneak + right-click: The World Slicer
 */
public class SwordOfRevengeItem extends SwordItem {
    public static final double REACH = 14.0;

    private static final ToolMaterial MATERIAL = new ToolMaterial() {
        public int getDurability() { return 0; }                // unbreakable
        public float getMiningSpeedMultiplier() { return 1f; }
        public float getAttackDamage() { return 0f; }
        public int getMiningLevel() { return 0; }
        public int getEnchantability() { return 0; }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public SwordOfRevengeItem(Settings settings) { super(MATERIAL, 7, -2.6f, settings); }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient && target.isAlive() && target.getHealth() > 1.0f) {
            target.setHealth(1.0f); // half a heart
            if (target.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
            }
        }
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && world instanceof ServerWorld sw) {
            if (user.isSneaking()) {
                Effects.spawnWave(sw, user);
                user.getItemCooldownManager().set(this, 160);
            } else {
                Effects.spawnPurple(sw, user);
                user.getItemCooldownManager().set(this, 100);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    /** Long-reach attack: raycast REACH blocks and hit the first entity (blocks in the way stop it). */
    public static void longSwing(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1f);
        Vec3d end = start.add(look.multiply(REACH));
        BlockHitResult bh = sw.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d limit = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();

        EntityHitResult eh = ProjectileUtil.raycast(player, start, limit,
                player.getBoundingBox().stretch(look.multiply(REACH)).expand(1.0),
                e -> !e.isSpectator() && e.canHit(), REACH * REACH);

        // slash trail
        double len = start.distanceTo(limit);
        for (double d = 1; d < len; d += 0.8) {
            Vec3d p = start.add(look.multiply(d));
            sw.spawnParticles(ParticleTypes.CRIT, p.x, p.y - 0.3, p.z, 1, 0, 0, 0, 0);
        }
        if (eh != null) {
            Entity target = eh.getEntity();
            player.attack(target);
        }
    }
}
