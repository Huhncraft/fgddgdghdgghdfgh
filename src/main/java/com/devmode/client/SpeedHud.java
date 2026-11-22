package com.devmode.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class SpeedHud {
    public static void render(DrawContext ctx, float tickDelta) {
        if (!DevModeClient.active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        double s = MovementController.speed;                       // blocks / second
        int color = s < 12 ? 0x55FF55 : s < 24 ? 0xFFFF55 : s < 36 ? 0xFFAA00 : 0xFF5555;
        String text = String.format("SPEED  %.1f b/s", s);

        ctx.getMatrices().push();
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, text, ctx.getScaledWindowWidth() / 4, (ctx.getScaledWindowHeight() - 75) / 2, color);
        ctx.getMatrices().pop();
    }
}
