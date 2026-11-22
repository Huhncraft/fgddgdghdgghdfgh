package com.devmode;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class DevModeMod implements ModInitializer {
    public static final String MOD_ID = "devmode";
    public static final Identifier STATE_PACKET = new Identifier(MOD_ID, "state");
    public static final Identifier SWING_PACKET = new Identifier(MOD_ID, "long_swing");

    public static Block START_MODE_DEV;
    public static Item START_MODE_DEV_ITEM, SWORD_OF_REVENGE, PISTOL, SHOTGUN, SNIPER, POCKET_KNIFE;
    public static EntityType<PocketKnifeEntity> POCKET_KNIFE_ENTITY;

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    @Override
    public void onInitialize() {
        // ---- block ----
        START_MODE_DEV = Registry.register(Registries.BLOCK, id("start_mode_dev"),
                new StartModeDevBlock(FabricBlockSettings.create().mapColor(MapColor.PURPLE).strength(3f).luminance(s -> 10)));
        START_MODE_DEV_ITEM = Registry.register(Registries.ITEM, id("start_mode_dev"),
                new BlockItem(START_MODE_DEV, new Item.Settings()));

        // ---- items ----
        SWORD_OF_REVENGE = Registry.register(Registries.ITEM, id("sword_of_revenge"),
                new SwordOfRevengeItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
        //                                                          dmg  pellets spread range cooldown
        PISTOL  = Registry.register(Registries.ITEM, id("pistol"),
                new GunItem(new Item.Settings().maxCount(1), 7f, 1, 0.01, 60, 6));
        SHOTGUN = Registry.register(Registries.ITEM, id("shotgun"),
                new GunItem(new Item.Settings().maxCount(1), 4f, 9, 0.10, 25, 22));
        SNIPER  = Registry.register(Registries.ITEM, id("sniper"),
                new GunItem(new Item.Settings().maxCount(1), 40f, 1, 0.0, 160, 45));
        POCKET_KNIFE = Registry.register(Registries.ITEM, id("pocket_knife"),
                new PocketKnifeItem(new Item.Settings().maxCount(16)));

        POCKET_KNIFE_ENTITY = Registry.register(Registries.ENTITY_TYPE, id("pocket_knife"),
                FabricEntityTypeBuilder.<PocketKnifeEntity>create(SpawnGroup.MISC, PocketKnifeEntity::new)
                        .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                        .trackRangeChunks(4).trackedUpdateRate(10).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(SWORD_OF_REVENGE); e.add(PISTOL); e.add(SHOTGUN); e.add(SNIPER); e.add(POCKET_KNIFE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(START_MODE_DEV_ITEM));

        // ---- command ----
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                CommandManager.literal("devmode")
                        .executes(ctx -> { DevMode.toggle(ctx.getSource().getPlayerOrThrow()); return 1; })
                        .then(CommandManager.literal("on").executes(ctx -> { DevMode.set(ctx.getSource().getPlayerOrThrow(), true, true); return 1; }))
                        .then(CommandManager.literal("off").executes(ctx -> { DevMode.set(ctx.getSource().getPlayerOrThrow(), false, true); return 1; }))
                        .then(CommandManager.literal("give").requires(s -> s.hasPermissionLevel(2)).executes(DevModeMod::give))));

        // ---- networking / lifecycle ----
        ServerPlayNetworking.registerGlobalReceiver(SWING_PACKET, (server, player, handler, buf, sender) -> {
            if (player.getMainHandStack().isOf(SWORD_OF_REVENGE)) SwordOfRevengeItem.longSwing(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DevMode.set(handler.player, false, false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DevMode.forget(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> DevMode.set(now, DevMode.isActive(old), false));
        ServerTickEvents.END_WORLD_TICK.register(Effects::tick);
    }

    private static int give(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        for (Item i : new Item[]{SWORD_OF_REVENGE, PISTOL, SHOTGUN, SNIPER, START_MODE_DEV_ITEM}) p.giveItemStack(new ItemStack(i));
        p.giveItemStack(new ItemStack(POCKET_KNIFE, 16));
        ctx.getSource().sendFeedback(() -> Text.literal("Gave Dev Mode gear."), false);
        return 1;
    }
}
