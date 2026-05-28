package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * Structure Terminal item that provides a unified UI for multiblock
 * structure preview and auto-build.
 *
 * <p>Usage:
 * <ul>
 *   <li>Right-click a multiblock host → opens Structure Terminal UI.</li>
 *   <li>Sneak + right-click a multiblock host → executes auto-build directly.</li>
 *   <li>Right-click anything else → passes through (no-op).</li>
 * </ul>
 * All build logic executes server-side.
 * </p>
 */
public class StructureTerminalItem extends Item {

    public StructureTerminalItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        var player = ctx.getPlayer();

        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof INEMultiblockBuildHost host)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (player.isShiftKeyDown()) {
            // Sneak + right-click → execute auto-build
            host.autoBuild(serverPlayer);
        } else {
            // Normal right-click → open Structure Terminal UI
            Component title = host.getHostBlockState().getBlock().getName();
            NetworkHooks.openScreen(
                serverPlayer,
                new SimpleMenuProvider(
                    (windowId, inv, p) ->
                        new cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu(windowId, inv, pos),
                    title
                ),
                buf -> buf.writeBlockPos(pos)
            );
        }

        return InteractionResult.CONSUME;
    }
}
