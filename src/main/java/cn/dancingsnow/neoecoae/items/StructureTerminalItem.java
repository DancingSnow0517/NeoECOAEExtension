package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
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
    public static final String TAG_HOST_TYPE = "HostType";
    public static final String TAG_HOST_TIER = "HostTier";
    public static final String TAG_OPERATION_MODE = "OperationMode";
    public static final int DEFAULT_BUILD_LENGTH = 1;
    public static final int MIN_BUILD_LENGTH = 1;

    public StructureTerminalItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    // ── Global max build length (repeat count / variable sections) ──

    /** Maximum repeat count across all three multiblock systems. */
    public static int getGlobalMaxBuildLength() {
        int crafting = Math.max(1, NEConfig.craftingSystemMaxLength - 3);
        int computation = Math.max(1, NEConfig.computationSystemMaxLength - 3);
        int storage = Math.max(1, NEConfig.storageSystemMaxLength - 3);
        return Math.max(storage, Math.max(crafting, computation));
    }

    // ── ItemStack NBT helpers (length = repeat count / variable sections) ──

    public static int getBuildLength(ItemStack stack) {
        return getBuildLength(stack, getMaxBuildLength(stack));
    }

    public static int getBuildLength(ItemStack stack, int maxLength) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BUILD_LENGTH)) {
            return DEFAULT_BUILD_LENGTH;
        }
        return Mth.clamp(tag.getInt(TAG_BUILD_LENGTH), MIN_BUILD_LENGTH, Math.max(MIN_BUILD_LENGTH, maxLength));
    }

    public static void setBuildLength(ItemStack stack, int length) {
        stack.getOrCreateTag().putInt(TAG_BUILD_LENGTH, Mth.clamp(length, MIN_BUILD_LENGTH, getMaxBuildLength(stack)));
    }

    public static StructureTerminalHostType getHostType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_HOST_TYPE)) {
            return StructureTerminalHostType.DEFAULT;
        }
        return StructureTerminalHostType.fromName(tag.getString(TAG_HOST_TYPE));
    }

    public static int getHostTier(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_HOST_TIER)) {
            return StructureTerminalHostType.DEFAULT_TIER;
        }
        return StructureTerminalHostType.clampTier(tag.getInt(TAG_HOST_TIER));
    }

    public static int getMaxBuildLength(ItemStack stack) {
        return getHostType(stack).maxBuildLength(getHostTier(stack));
    }

    public static void setHostType(ItemStack stack, StructureTerminalHostType hostType) {
        setHostTarget(stack, hostType, getHostTier(stack));
    }

    public static void setHostTarget(ItemStack stack, StructureTerminalHostType hostType, int tier) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_HOST_TYPE, hostType.name());
        tag.putInt(TAG_HOST_TIER, StructureTerminalHostType.clampTier(tier));
        setBuildLength(stack, getBuildLength(stack));
    }

    public static StructureTerminalMode getOperationMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_OPERATION_MODE)) {
            return StructureTerminalMode.BUILD;
        }
        return StructureTerminalMode.fromName(tag.getString(TAG_OPERATION_MODE));
    }

    public static void setOperationMode(ItemStack stack, StructureTerminalMode mode) {
        stack.getOrCreateTag().putString(TAG_OPERATION_MODE, mode.name());
    }

    private static void setHostTarget(ItemStack stack, INEMultiblockBuildHost host) {
        MultiBlockDefinition definition = host.getBuildDefinition();
        StructureTerminalHostType hostType = StructureTerminalHostType.fromDefinition(definition);
        if (hostType != null) {
            setHostTarget(stack, hostType, StructureTerminalHostType.tierFromDefinition(definition));
        }
    }

    // ── Item behaviour ──

    /**
     * Right-click in air → open terminal config UI.
     * Shift + right-click in air → no action (blocked).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Shift + air → do nothing, prevent config UI
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

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
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof INEMultiblockBuildHost host) {
                    setHostTarget(stack, host);
                }
                openTerminalConfig(serverPlayer, ctx.getHand());
            }
            return InteractionResult.CONSUME;
        }

        // Shift + right-click → try to build
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof INEMultiblockBuildHost host)) {
            // Not a host → consume to block block's own interaction / UI
            return InteractionResult.CONSUME;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        setHostTarget(stack, host);
        int requestedLength = StructureTerminalItem.getBuildLength(stack, host.getMaxBuildLength());
        ServerPlayer serverPlayer = (ServerPlayer) player;
        switch (StructureTerminalItem.getOperationMode(stack)) {
            case BUILD -> host.autoBuild(serverPlayer, requestedLength, false);
            case MIRRORED_BUILD -> host.autoBuild(serverPlayer, requestedLength, true);
            case DISMANTLE -> host.dismantle(serverPlayer);
        }

        return InteractionResult.CONSUME;
    }

    // ── Internal ──

    private void openTerminalConfig(ServerPlayer player, InteractionHand hand) {
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (windowId, inv, p) -> new NEStructureTerminalMenu(windowId, inv, hand),
                        Component.translatable("item.neoecoae.structure_terminal")),
                buf -> buf.writeEnum(hand));
    }
}
