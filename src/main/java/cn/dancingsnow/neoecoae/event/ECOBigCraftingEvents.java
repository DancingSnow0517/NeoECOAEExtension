package cn.dancingsnow.neoecoae.event;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.PatternBusUpdateScheduler;
import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrders;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.math.BigInteger;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public final class ECOBigCraftingEvents {
    private ECOBigCraftingEvents() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("neoecoae");
        var craft = Commands.literal("bigcraft");
        craft.then(Commands.literal("start")
                .then(Commands.argument("controller", BlockPosArgument.blockPos())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> {
                                    var source = context.getSource();
                                    var player = source.getPlayerOrException();
                                    var pos = BlockPosArgument.getLoadedBlockPos(context, "controller");
                                    if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64
                                            || !(source.getLevel().getBlockEntity(pos) instanceof NEBlockEntity host)
                                            || host.getGridNode() == null
                                            || !host.getGridNode().isActive()) {
                                        source.sendFailure(Component.literal("请在 8 格内指定已联网的 ECO 方块。"));
                                        return 0;
                                    }
                                    var key = AEItemKey.of(player.getMainHandItem());
                                    if (key == null) {
                                        source.sendFailure(Component.literal("请手持需要合成的物品作为模板。"));
                                        return 0;
                                    }
                                    try {
                                        String input = StringArgumentType.getString(context, "amount");
                                        if (input.length() > 4096 || !input.matches("[0-9]+"))
                                            throw new IllegalArgumentException();
                                        var amount = new BigInteger(input);
                                        var data = ECOBigCraftingOrders.get(source.getServer());
                                        var id = data.add(source.getLevel(), pos, player.getUUID(), key, amount);
                                        source.sendSuccess(
                                                () -> Component.literal("大整数合成订单：" + id + "，数量：" + amount), false);
                                        return 1;
                                    } catch (IllegalArgumentException invalid) {
                                        source.sendFailure(Component.literal("数量必须是正整数（最多 4096 位）。"));
                                        return 0;
                                    }
                                }))));
        for (String action : new String[] {"status", "cancel", "resume"}) {
            craft.then(Commands.literal(action)
                    .then(Commands.argument("id", UuidArgument.uuid()).executes(context -> {
                        var source = context.getSource();
                        var player = source.getPlayerOrException();
                        var data = ECOBigCraftingOrders.get(source.getServer());
                        var id = UuidArgument.getUuid(context, "id");
                        try {
                            if (action.equals("cancel")) data.cancel(id, player.getUUID(), source.getServer());
                            if (action.equals("resume")) data.resume(id, player.getUUID());
                            String message = data.describe(id, player.getUUID());
                            source.sendSuccess(() -> Component.literal(message), false);
                            return 1;
                        } catch (IllegalArgumentException invalid) {
                            source.sendFailure(Component.literal(invalid.getMessage()));
                            return 0;
                        }
                    })));
        }
        event.getDispatcher().register(root.then(craft));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) PatternBusUpdateScheduler.tick(server);
        if (server != null && server.getTickCount() % 20 == 0)
            ECOBigCraftingOrders.get(server).tick(server);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        PatternBusUpdateScheduler.clear(event.getServer());
        ECOBigCraftingOrders.get(event.getServer()).stop();
    }
}
