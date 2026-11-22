package com.devmode;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.*;

/** Ticking ability effects: Hollow Purple and The World Slicer. */
public final class Effects {
    private static final List<Purple> PURPLES = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Random RNG = new Random();

    public static void spawnPurple(ServerWorld w, LivingEntity caster) {
        Vec3d look = caster.getRotationVec(1f);
        PURPLES.add(new Purple(w, caster.getEyePos().add(look.multiply(2)), look, caster));
        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2f, 0.6f);
    }

    /** Random angle, random speed. */
    public static void spawnWave(ServerWorld w, LivingEntity caster) {
        double angle = RNG.nextDouble() * Math.PI * 2;
        double speed = 0.45 + RNG.nextDouble();          // 0.45 .. 1.45 blocks per tick (9 .. 29 b/s)
        WAVES.add(new Wave(w, caster.getPos(), angle, speed, caster));
        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2f, 0.5f);
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(caster) < 60 * 60)) {
            p.sendMessage(Text.literal("THE WORLD SLICER - JUMP!").formatted(Formatting.RED, Formatting.BOLD), true);
        }
    }

    public static void tick(ServerWorld w) {
        PURPLES.removeIf(p -> p.world == w && p.tick());
        WAVES.removeIf(x -> x.world == w && x.tick());
    }

    // ------------------------------------------------------------------ Hollow Purple
    private static final class Purple {
        final ServerWorld world; final Vec3d dir; final LivingEntity owner;
        Vec3d pos; int age;

        Purple(ServerWorld w, Vec3d pos, Vec3d dir, LivingEntity owner) { this.world = w; this.pos = pos; this.dir = dir; this.owner = owner; }

        boolean tick() {
            pos = pos.add(dir.multiply(1.1));
            age++;
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 40, 1.2, 1.2, 1.2, 0.03);
            world.spawnParticles(ParticleTypes.WITCH, pos.x, pos.y, pos.z, 25, 1.5, 1.5, 1.5, 0.1);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 20, 0.8, 0.8, 0.8, 0.1);
            if (age % 5 == 0) world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 3f, 0.5f);

            if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                int r = 3;
                BlockPos c = BlockPos.ofFloored(pos);
                for (BlockPos bp : BlockPos.iterate(c.add(-r, -r, -r), c.add(r, r, r))) {
                    double dx = bp.getX() + 0.5 - pos.x, dy = bp.getY() + 0.5 - pos.y, dz = bp.getZ() + 0.5 - pos.z;
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    BlockState st = world.getBlockState(bp);
                    if (!st.isAir() && st.getHardness(world, bp) >= 0) {
                        world.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            Box box = new Box(pos.x - 4, pos.y - 4, pos.z - 4, pos.x + 4, pos.y + 4, pos.z + 4);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x != owner && x.isAlive())) {
                e.damage(world.getDamageSources().magic(), 200f);
            }
            if (age >= 70) {
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ The World Slicer
    /**
     * A flat wall of force that sweeps the ground. Anything standing on the ground
     * when the front passes it is hit; anything in the air (jumping) is not.
     */
    private static final class Wave {
        static final double LENGTH = 50, HALF = 26;
        final ServerWorld world; final Vec3d origin, dir, perp; final double speed, baseY; final LivingEntity owner;
        final Set<UUID> hit = new HashSet<>();
        double front = 0;

        Wave(ServerWorld w, Vec3d casterPos, double angle, double speed, LivingEntity owner) {
            this.world = w; this.speed = speed; this.owner = owner;
            this.dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            this.perp = new Vec3d(-dir.z, 0, dir.x);
            this.origin = casterPos.subtract(dir.multiply(20)); // starts 20 blocks away, sweeps through the caster
            this.baseY = casterPos.y;
        }

        boolean tick() {
            front += speed;
            Vec3d c = origin.add(dir.multiply(front));

            for (double s = -HALF; s <= HALF; s += 1.0) {
                Vec3d p = c.add(perp.multiply(s));
                world.spawnParticles(ParticleTypes.CLOUD, p.x, baseY + 0.2, p.z, 1, 0.1, 0.05, 0.1, 0);
                if (((int) (s + HALF)) % 3 == 0) world.spawnParticles(ParticleTypes.CRIT, p.x, baseY + 0.6, p.z, 2, 0.1, 0.3, 0.1, 0.2);
            }

            Box box = new Box(c.x - HALF - 2, baseY - 3, c.z - HALF - 2, c.x + HALF + 2, baseY + 4, c.z + HALF + 2);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (e.isSpectator() || hit.contains(e.getUuid())) continue;
                Vec3d rel = e.getPos().subtract(origin);
                double along = rel.dot(dir), across = rel.dot(perp);
                if (Math.abs(across) > HALF) continue;
                if (along <= front - speed || along > front) continue; // bands tile exactly: each entity is tested once
                if (!e.isOnGround()) continue;                         // airborne -> jumped over it
                hit.add(e.getUuid());
                e.damage(world.getDamageSources().magic(), 10f);
                e.addVelocity(dir.x * 1.0, 0.5, dir.z * 1.0);
                e.velocityModified = true;
            }
            return front >= LENGTH;
        }
    }
}
