package com.devmode.client;

import com.devmode.DevModeMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/** Client-side movement tech: speed measurement, bunnyhop, air strafing, wall kicks, long sword swing. */
public final class MovementController {
    public static double speed;                      // smoothed horizontal speed, blocks per second

    private static double px, pz;
    private static boolean havePos, wasOnGround, prevJump, prevAttack;
    private static double momentum;                  // horizontal speed (blocks/tick) kept across landings
    private static int groundTicks, kickCooldown;

    private static final double MAX_SPEED = 1.8;     // blocks per tick (36 b/s) bunnyhop cap
    private static final double HOP_GAIN = 1.05;     // x speed per perfect hop
    private static final double AIR_CAP = 0.075;     // source-style air strafing
    private static final double AIR_ACCEL = 0.014;

    public static void reset() { speed = 0; momentum = 0; havePos = false; kickCooldown = 0; }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) { reset(); return; }

        // --- speed counter ---
        if (havePos) speed += (Math.hypot(p.getX() - px, p.getZ() - pz) * 20 - speed) * 0.35;
        px = p.getX(); pz = p.getZ(); havePos = true;

        boolean jumpHeld = mc.options.jumpKey.isPressed();
        boolean jumpEdge = jumpHeld && !prevJump;
        prevJump = jumpHeld;

        longSwing(mc, p);

        if (!DevModeClient.active || p.isSpectator() || p.getAbilities().flying || p.isFallFlying()
                || p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.hasVehicle()) {
            wasOnGround = p.isOnGround();
            return;
        }
        if (kickCooldown > 0) kickCooldown--;

        Vec3d v = p.getVelocity();
        double hs = Math.hypot(v.x, v.z);
        boolean ground = p.isOnGround();
        float fwd = p.input.movementForward, side = p.input.movementSideways;
        boolean moving = fwd != 0 || side != 0;
        double yaw = Math.toRadians(p.getYaw());
        double wx = side * Math.cos(yaw) - fwd * Math.sin(yaw);
        double wz = fwd * Math.cos(yaw) + side * Math.sin(yaw);
        double wl = Math.hypot(wx, wz);

        if (ground) {
            groundTicks++;
            if (groundTicks > 3) momentum = hs;        // standing around resets the chain
        } else {
            // ---- BUNNYHOP: the tick we leave the ground with upward velocity ----
            if (wasOnGround && v.y > 0.2 && moving) {
                double target = Math.min(Math.max(hs, momentum * 0.98) * HOP_GAIN, MAX_SPEED);
                if (hs > 0.01) v = new Vec3d(v.x / hs * target, v.y, v.z / hs * target);
                else if (wl > 0) v = new Vec3d(wx / wl * target, v.y, wz / wl * target);
            }
            groundTicks = 0;

            // ---- AIR STRAFE: steer while keeping / gaining speed ----
            if (wl > 0) {
                double nx = wx / wl, nz = wz / wl;
                double proj = v.x * nx + v.z * nz;
                double add = Math.max(0, Math.min(AIR_CAP - proj, AIR_ACCEL));
                v = v.add(nx * add, 0, nz * add);
            }

            // ---- WALL KICK: jump while airborne and touching a wall ----
            if (jumpEdge && !wasOnGround && kickCooldown <= 0) {
                double nx = 0, nz = 0;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : dirs) {
                    if (!p.getWorld().isSpaceEmpty(p, p.getBoundingBox().offset(d[0] * 0.12, 0, d[1] * 0.12))) { nx -= d[0]; nz -= d[1]; }
                }
                double nl = Math.hypot(nx, nz);
                if (nl > 0) {
                    nx /= nl; nz /= nl;
                    double lx = -Math.sin(yaw), lz = Math.cos(yaw);
                    double ox = nx * 0.8 + lx * 0.6, oz = nz * 0.8 + lz * 0.6;
                    double ol = Math.hypot(ox, oz);
                    double sp = Math.min(Math.max(0.55, Math.hypot(v.x, v.z) * 1.03), MAX_SPEED);
                    v = new Vec3d(ox / ol * sp, 0.65, oz / ol * sp);
                    kickCooldown = 5;
                    p.fallDistance = 0;
                    p.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 1.4f);
                    for (int i = 0; i < 6; i++)
                        p.getWorld().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), -nx * 0.1 * i, 0.05, -nz * 0.1 * i);
                }
            }
            p.setVelocity(v);
            momentum = Math.hypot(v.x, v.z);
        }
        wasOnGround = ground;
    }

    /** Sword of Revenge: left-click that doesn't hit anything nearby asks the server for a long-reach swing. */
    private static void longSwing(MinecraftClient mc, ClientPlayerEntity p) {
        boolean atk = mc.options.attackKey.isPressed();
        boolean edge = atk && !prevAttack;
        prevAttack = atk;
        if (edge && mc.currentScreen == null && p.getMainHandStack().isOf(DevModeMod.SWORD_OF_REVENGE)) {
            HitResult h = mc.crosshairTarget;
            if (h == null || h.getType() != HitResult.Type.ENTITY) {
                ClientPlayNetworking.send(DevModeMod.SWING_PACKET, PacketByteBufs.create());
            }
        }
    }
}
