package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
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

    public NEStructureTerminalMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(NENativeMenus.STRUCTURE_TERMINAL.get(), containerId);
        this.hand = hand;
        this.buildLength = StructureTerminalItem.getBuildLength(playerInv.player.getItemInHand(hand));
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

    /**
     * Returns the Structure Terminal ItemStack this menu is bound to, or null.
     */
    @Nullable
    public ItemStack getTerminalStack(Player player) {
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
     * Sends the current build length state to the client.
     */
    public void syncToClient(ServerPlayer player) {
        cn.dancingsnow.neoecoae.network.NENetwork.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new cn.dancingsnow.neoecoae.network.NENetwork.NEStructureTerminalConfigPacket(buildLength)
        );
    }
}
