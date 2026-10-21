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
 * crafting. AE2LT 2.1 and AE2 Utility also target this legacy class with injections. The implementation remains in
 * {@code crafting.execution}; its subclass mirrors the legacy job reference so those accessors continue
 * to observe the live state. The guarded dispatch anchors retain the bytecode targets expected by already
 * released AE2LT and AE2 Utility builds. Persistence keeps the original registry-aware ABI and delegates to
 * the active platform implementation, so addon persistence injections surround the real save/load path.</p>
 */
@Deprecated(forRemoval = false)
public abstract class ECOCraftingCPULogic {
    protected ExecutingCraftingJob job;

    protected ECOCraftingCPULogic() {
    }

    protected void finishJob(boolean success) {
    }

    /** Legacy AE2LT and AE2 Utility injection target; the active implementation overrides this method. */
    public long insert(AEKey what, long amount, Actionable type) {
        if (ae2ltLegacyMixinAnchorEnabled()) {
            ListCraftingInventory waitingFor = null;
            waitingFor.extract(what, amount, type);
            what.matches((GenericStack) null);
        }
        return 0L;
    }

    /** Legacy AE2LT injection target; current crafting dispatch runs in the execution subclass. */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        if (ae2ltLegacyMixinAnchorEnabled()) {
            ICraftingProvider provider = null;
            provider.pushPattern(null, (KeyCounter[]) null);
        }
        return 0;
    }

    /** Registry-aware addon hook surrounding the real Forge persistence implementation. */
    public final void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        readNativePersistentData(data);
    }

    public final void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        writeNativePersistentData(data);
    }

    protected abstract void readNativePersistentData(CompoundTag data);

    protected abstract void writeNativePersistentData(CompoundTag data);

    private static boolean ae2ltLegacyMixinAnchorEnabled() {
        return false;
    }
}
