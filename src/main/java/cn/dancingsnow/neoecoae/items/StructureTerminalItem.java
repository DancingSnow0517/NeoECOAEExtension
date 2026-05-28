package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * Structure Terminal — a handheld tool for configuring and executing
 * multiblock structure builds.
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>Right-click (air or block, no shift)</b>: Opens the terminal
 *       config UI to set the build length. The length is stored in the
 *       item's NBT under key {@value #TAG_BUILD_LENGTH}.</li>
 *   <li><b>Shift + right-click on a multiblock host</b>: Executes
 *       {@link INEMultiblockBuildHost#autoBuild(ServerPlayer, int)}
 *       using the length stored in this item.</li>
 *   <li><b>Shift + right-click on a non-host block</b>: Passes through.</li>
 * </ul>
 */
public class StructureTerminalItem extends Item {

    public static final String TAG_BUILD_LENGTH = "BuildLength";
    private static final int DEFAULT_BUILD_LENGTH = 1;
    private static final int MAX_GLOBAL_LENGTH = 64;

    public StructureTerminalItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    // ── ItemStack NBT helpers ──

    public static int getBuildLength(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BUILD_LENGTH)) {
            return DEFAULT_BUILD_LENGTH;
        }
        return Mth.clamp(tag.getInt(TAG_BUILD_LENGTH), 1, MAX_GLOBAL_LENGTH);
    }

    public static void setBuildLength(ItemStack stack, int length) {
        stack.getOrCreateTag().putInt(TAG_BUILD_LENGTH, Mth.clamp(length, 1, MAX_GLOBAL_LENGTH));
    }

    // ── Item behaviour ──

    /**
     * Right-click in air → open terminal config UI.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            openTerminalConfig(serverPlayer, hand);
            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    /**
     * Right-click on a block.
     * <ul>
     *   <li>No shift → open terminal config UI (same as air right-click).</li>
     *   <li>Shift + host → execute auto-build with stored length.</li>
     *   <li>Shift + non-host → PASS.</li>
     * </ul>
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();

        if (!player.isShiftKeyDown()) {
            // Normal right-click on any block → open config UI
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                openTerminalConfig(serverPlayer, ctx.getHand());
            }
            return InteractionResult.CONSUME;
        }

        // Shift + right-click → try to build
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof INEMultiblockBuildHost host)) {
            // Not a host → pass through with a hint
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                    Component.translatable("gui.neoecoae.terminal.not_a_host"), true);
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int requestedLength = StructureTerminalItem.getBuildLength(stack);
        ServerPlayer serverPlayer = (ServerPlayer) player;
        host.autoBuild(serverPlayer, requestedLength);

        return InteractionResult.CONSUME;
    }

    // ── Internal ──

    private void openTerminalConfig(ServerPlayer player, InteractionHand hand) {
        NetworkHooks.openScreen(
            player,
            new SimpleMenuProvider(
                (windowId, inv, p) -> new NEStructureTerminalMenu(windowId, inv, hand),
                Component.translatable("item.neoecoae.structure_terminal")
            ),
            buf -> buf.writeEnum(hand)
        );
    }
}
