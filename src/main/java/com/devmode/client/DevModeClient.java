package com.devmode.client;

import com.devmode.DevModeMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class DevModeClient implements ClientModInitializer {
    /** Set by the server when the player enters / leaves Dev Mode. */
    public static volatile boolean active = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(DevModeMod.POCKET_KNIFE_ENTITY, FlyingItemEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(DevModeMod.STATE_PACKET, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> { active = on; if (!on) MovementController.reset(); });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { active = false; MovementController.reset(); });

        ClientTickEvents.END_CLIENT_TICK.register(MovementController::tick);
        HudRenderCallback.EVENT.register(SpeedHud::render);
    }
}
