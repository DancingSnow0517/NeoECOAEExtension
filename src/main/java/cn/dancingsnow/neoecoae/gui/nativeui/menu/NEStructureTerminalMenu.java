package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the Structure Terminal configuration UI.
 * <p>
 * This menu is bound to a {@link StructureTerminalItem} in the player's
 * hand (identified by {@link InteractionHand}), not to a world BlockEntity.
 * It allows the player to set the build length stored in the item's NBT.
 * </p>
 */
public class NEStructureTerminalMenu extends AbstractContainerMenu {

    private final InteractionHand hand;
    private int buildLength;
    private int minLength = StructureTerminalItem.MIN_BUILD_LENGTH;
    private int maxLength = 1;
    private StructureTerminalHostType hostType = StructureTerminalHostType.DEFAULT;
    private StructureTerminalMode operationMode = StructureTerminalMode.BUILD;
    private java.util.List<NEStructureTerminalUiState.BuildMaterialEntry> materials = java.util.List.of();

    public NEStructureTerminalMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(NENativeMenus.STRUCTURE_TERMINAL.get(), containerId);
        this.hand = hand;
        ItemStack stack = playerInv.player.getItemInHand(hand);
        this.hostType = StructureTerminalItem.getHostType(stack);
        this.operationMode = StructureTerminalItem.getOperationMode(stack);
        this.maxLength = StructureTerminalItem.getMaxBuildLength(stack);
        this.buildLength = StructureTerminalItem.getBuildLength(stack);
        if (!playerInv.player.level().isClientSide() && playerInv.player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public InteractionHand getHand() {
        return hand;
    }

    public int getBuildLength() {
        return buildLength;
    }

    public void setBuildLength(int length) {
        this.buildLength = length;
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public StructureTerminalHostType getHostType() {
        return hostType;
    }

    public StructureTerminalMode getOperationMode() {
        return operationMode;
    }

    public java.util.List<NEStructureTerminalUiState.BuildMaterialEntry> getMaterials() {
        return materials;
    }

    public void setClientConfig(
            int length,
            int minLength,
            int maxLength,
            StructureTerminalHostType hostType,
            StructureTerminalMode operationMode,
            java.util.List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        this.buildLength = length;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.hostType = hostType;
        this.operationMode = operationMode;
        this.materials = java.util.List.copyOf(materials);
    }

    /**
     * Returns the Structure Terminal ItemStack this menu is bound to, or null.
     */
    @Nullable public ItemStack getTerminalStack(Player player) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof StructureTerminalItem) {
            return stack;
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof StructureTerminalItem;
    }

    /**
     * Sends the current build length state to the client,
     * reading directly from the ItemStack NBT to avoid stale cache.
     */
    public void syncToClient(ServerPlayer player) {
        ItemStack stack = getTerminalStack(player);
        int length = stack != null
                ? StructureTerminalItem.getBuildLength(stack)
                : StructureTerminalItem.DEFAULT_BUILD_LENGTH;
        int min = StructureTerminalItem.MIN_BUILD_LENGTH;
        int max = stack != null
                ? StructureTerminalItem.getMaxBuildLength(stack)
                : StructureTerminalItem.getGlobalMaxBuildLength();
        StructureTerminalHostType target =
                stack != null ? StructureTerminalItem.getHostType(stack) : StructureTerminalHostType.DEFAULT;
        StructureTerminalMode mode =
                stack != null ? StructureTerminalItem.getOperationMode(stack) : StructureTerminalMode.BUILD;
        int tier = stack != null ? StructureTerminalItem.getHostTier(stack) : StructureTerminalHostType.DEFAULT_TIER;
        java.util.List<NEStructureTerminalUiState.BuildMaterialEntry> materialEntries = stack != null
                ? StructureTerminalMaterialRequirements.collect(player, target, tier, length)
                : java.util.List.of();
        this.buildLength = length;
        this.minLength = min;
        this.maxLength = max;
        this.hostType = target;
        this.operationMode = mode;
        this.materials = materialEntries;
        cn.dancingsnow.neoecoae.network.NENetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new cn.dancingsnow.neoecoae.network.NENetwork.NEStructureTerminalConfigPacket(
                        length, min, max, target, mode, materialEntries));
    }
}
