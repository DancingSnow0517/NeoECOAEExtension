package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.items.contents.CellConfig;
import appeng.me.cells.CreativeCellHandler;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOUnboundedSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** ECO-only view of an AE2 creative cell; AE2 owns the generated supply. */
public final class ECOCreativeCell implements IECOStorageCell, IECOUnboundedSource, ExactAmountSource {
    private static final ECOCellType TYPE = new ECOCellType(
            new ResourceLocation("ae2", "creative_cell"),
            Component.translatable("item.ae2.creative_cell"), 1);
    private final StorageCell delegate;
    private final Set<AEKey> configured;
    private final int configSlots;

    ECOCreativeCell(StorageCell delegate, int configSlots) {
        this.delegate = delegate;
        this.configured = Set.copyOf(delegate.getAvailableStacks().keySet());
        this.configSlots = configSlots;
    }

    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }
    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }
    @Override public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : configured) out.set(key, Long.MAX_VALUE);
    }
    public Set<AEKey> configuredKeys() { return configured; }
    @Override public java.math.BigInteger neoecoae$getExactAmount(AEKey key) {
        return configured.contains(key) ? java.math.BigInteger.valueOf(Long.MAX_VALUE) : java.math.BigInteger.ZERO;
    }
    @Override public ExactAmount neoecoae$getDisplayAmount(AEKey key) {
        return configured.contains(key) ? ExactAmount.unbounded() : ExactAmount.finite(java.math.BigInteger.ZERO);
    }
    @Override public Object neoecoae$exactInventoryIdentity() { return delegate; }
    @Override public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }
    @Override public CellState getStatus() { return delegate.getStatus(); }
    @Override public double getIdleDrain() { return delegate.getIdleDrain(); }
    @Override public boolean canFitInsideCell() { return false; }
    @Override public Component getDescription() { return delegate.getDescription(); }
    @Override public void persist() { delegate.persist(); }
    @Override public IECOTier getTier() { return ECOTier.L4; }
    @Override public ECOCellType getCellType() { return TYPE; }
    @Override public long getStoredItemTypes() { return configured.size(); }
    @Override public long getTotalItemTypes() { return configSlots; }
    @Override public boolean isInfiniteStorageEligible() { return false; }
    @Override public long getUsedBytes() { return 0; }
    @Override public long getTotalBytes() { return 0; }

    public enum Handler implements IECOCellHandler {
        INSTANCE;
        @Override public boolean isCell(ItemStack stack) {
            return CreativeCellHandler.INSTANCE.isCell(stack);
        }
        @Override public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            StorageCell delegate = CreativeCellHandler.INSTANCE.getCellInventory(stack, host);
            return delegate == null ? null : new ECOCreativeCell(delegate, CellConfig.create(stack).size());
        }
    }
}
