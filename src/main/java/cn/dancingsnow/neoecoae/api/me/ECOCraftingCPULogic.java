package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Binary compatibility target for integrations compiled against the original NeoECOAE package layout.
 *
 * <p>ExtendedAE Plus applies accessors to this exact class to inspect the active job and finish virtual
 * crafting. This class is also a current injection target for AE2LT and AE2 Utility. The implementation remains in
 * {@code crafting.execution}; its subclass mirrors the job reference so those accessors observe the live state.
 * The guarded call sites below are injection targets and are never executed by the current implementation.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPULogic {
    protected ExecutingCraftingJob job;

    protected ECOCraftingCPULogic() {
    }

    protected void finishJob(boolean success) {
    }

    /** Current injection target for AE2LT and AE2 Utility; the active implementation overrides this method. */
    public long insert(AEKey what, long amount, Actionable type) {
        if (ae2ltLegacyMixinAnchorEnabled()) {
            ListCraftingInventory waitingFor = null;
            waitingFor.extract(what, amount, type);
            what.matches((GenericStack) null);
        }
        return 0L;
    }

    /** Current injection target for AE2LT; crafting dispatch runs in the execution subclass. */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        if (ae2ltLegacyMixinAnchorEnabled()) {
            ICraftingProvider provider = null;
            provider.pushPattern(null, (KeyCounter[]) null);
        }
        return 0;
    }

    /** Current persistence injection targets for AE2LT. */
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
    }

    private static boolean ae2ltLegacyMixinAnchorEnabled() {
        return false;
    }
}
