package com.devmode;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side state of the gamemode. */
public final class DevMode {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final UUID SPEED_ID = UUID.fromString("a6b0c9b4-2f64-4e0a-9d79-6f0f9c1d7e01");
    /** +75% movement speed while in the mode. */
    private static final double SPEED_BONUS = 0.75;

    public static boolean isActive(ServerPlayerEntity p) { return ACTIVE.contains(p.getUuid()); }

    public static void toggle(ServerPlayerEntity p) { set(p, !isActive(p), true); }

    public static void forget(ServerPlayerEntity p) { ACTIVE.remove(p.getUuid()); }

    public static void set(ServerPlayerEntity p, boolean on, boolean announce) {
        if (on) ACTIVE.add(p.getUuid()); else ACTIVE.remove(p.getUuid());

        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            if (on) speed.addTemporaryModifier(new EntityAttributeModifier(
                    SPEED_ID, "devmode_speed", SPEED_BONUS, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ServerPlayNetworking.send(p, DevModeMod.STATE_PACKET, buf);

        if (announce) {
            p.sendMessage(Text.literal(on ? "DEV MODE ON - bunnyhop, wall kick, speed counter"
                    : "Dev mode off").formatted(on ? Formatting.LIGHT_PURPLE : Formatting.GRAY), true);
        }
    }
}
