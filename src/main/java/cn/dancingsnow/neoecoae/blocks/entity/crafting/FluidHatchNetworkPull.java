package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import org.jetbrains.annotations.Nullable;

/** Reconcile a configured stock target once per second, following GTM's ME input hatch model. */
final class FluidHatchNetworkPull {
    private FluidHatchNetworkPull() {}

    static void sync(LongFluidTank tank, @Nullable AEFluidKey selected, long target,
        MEStorage network, IEnergySource energy, IActionSource source) {
        long stored = tank.getStoredAmount();
        if (stored > 0) {
            AEFluidKey current = AEFluidKey.of(tank.getFluid());
            long excess = !current.equals(selected) ? stored : Math.max(0, stored - target);
            if (excess > 0) {
                long returned = network.insert(current, excess, Actionable.MODULATE, source);
                if (returned > 0) tank.remove(returned);
                // Preserve fluid the network could not accept, and retry next second.
                if (returned < excess) return;
            }
        }
        if (selected == null || target <= tank.getStoredAmount()) return;
        long extracted = StorageHelper.poweredExtraction(energy, network, selected,
            target - tank.getStoredAmount(), source);
        if (extracted > 0) tank.add(selected.toStack(1), extracted);
    }
}
