package cn.dancingsnow.neoecoae.menu;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Bridges the existing hatch configuration to AE2's real submenu, without InterfaceLogic. */
public final class FluidHatchStockAmount {
    private FluidHatchStockAmount() {}

    public static void open(ECOFluidInputHatchBlockEntity host, ServerPlayer player) {
        var key = AEFluidKey.of(host.getMarkedFluid());
        if (!host.hasNetworkPullCard() || key == null || !host.canPlayerUse(player)) return;
        SetStockAmountMenu.open(player, MenuLocators.forBlockEntity(host), 0, key,
            (int) Math.min(Integer.MAX_VALUE, host.getTargetAmount()));
    }

    public static boolean apply(ECOFluidInputHatchBlockEntity host, Player player,
        AbstractContainerMenu menu, @Nullable AEKey expected, long amount) {
        if (amount < 0 || !(expected instanceof AEFluidKey) || !host.hasNetworkPullCard()
            || !host.canPlayerUse(player) || player.containerMenu != menu || !menu.stillValid(player)
            || !Objects.equals(expected, host.getConfig().getKey(0))) return false;
        host.getConfig().setStack(0, amount == 0 ? null : new GenericStack(expected, amount));
        return true;
    }

    /** A menu view of the existing mark/target fields; no second inventory or copy of their state. */
    public static GenericStackInv configuration(ECOFluidInputHatchBlockEntity host) {
        return new GenericStackInv(Set.of(AEKeyType.fluids()), null, GenericStackInv.Mode.CONFIG_STACKS, 1) {
            @Override public @Nullable GenericStack getStack(int slot) {
                Objects.checkIndex(slot, size());
                var key = AEFluidKey.of(host.getMarkedFluid());
                return key == null ? null : new GenericStack(key, host.getTargetAmount());
            }

            @Override public @Nullable AEKey getKey(int slot) {
                var stack = getStack(slot);
                return stack == null ? null : stack.what();
            }

            @Override public long getAmount(int slot) {
                var stack = getStack(slot);
                return stack == null ? 0 : stack.amount();
            }

            @Override public long getMaxAmount(AEKey key) {
                return key instanceof AEFluidKey ? Long.MAX_VALUE : 0;
            }

            @Override public void setStack(int slot, @Nullable GenericStack stack) {
                Objects.checkIndex(slot, size());
                if (stack != null && (!(stack.what() instanceof AEFluidKey) || stack.amount() < 0)) return;
                boolean clear = stack == null || stack.amount() == 0;
                host.setMarkedFluid(clear ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                    : ((AEFluidKey) stack.what()).toStack(1));
                host.setTargetAmount(clear ? 0 : stack.amount());
            }
        };
    }
}
