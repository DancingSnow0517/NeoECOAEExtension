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
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** ECO-only view of an AE2 creative cell; AE2 remains responsible for all storage operations. */
public final class ECOCreativeCell implements IECOStorageCell {
    private static final ECOCellType TYPE = new ECOCellType(
        Component.translatable("item.ae2.creative_cell"), 1, false);
    private final StorageCell delegate;
    private final Set<AEKey> configured;
    private final int configSlots;

    ECOCreativeCell(StorageCell delegate, int configSlots) {
        this.delegate = delegate;
        this.configured = Set.copyOf(delegate.getAvailableStacks().keySet());
        this.configSlots = configSlots;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // Multiple creative cells and existing finite stacks must not wrap the aggregate negative.
        for (AEKey key : configured) out.set(key, Long.MAX_VALUE);
    }

    /** Internal crafting capability, deliberately separate from terminal quantity reporting. */
    public Set<AEKey> configuredKeys() {
        return configured;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public CellState getStatus() {
        return delegate.getStatus();
    }

    @Override
    public double getIdleDrain() {
        return delegate.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return delegate.canFitInsideCell();
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void persist() {
        delegate.persist();
    }

    @Override
    public IECOTier getTier() {
        // L9 is the visual level only; creative cells work with every ECO controller tier.
        return ECOTier.L4;
    }

    @Override
    public ECOCellType getCellType() {
        return TYPE;
    }

    @Override
    public long getStoredItemTypes() {
        return configured.size();
    }

    @Override
    public long getTotalItemTypes() {
        return configSlots;
    }

    @Override
    public boolean isInfiniteStorageEligible() {
        // Generated resources must never become persisted contents in a migration.
        return false;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalBytes() {
        return 0;
    }

    public enum Handler implements IECOCellHandler {
        INSTANCE;

        @Override
        public boolean isCell(ItemStack stack) {
            return CreativeCellHandler.INSTANCE.isCell(stack);
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            StorageCell delegate = CreativeCellHandler.INSTANCE.getCellInventory(stack, host);
            return delegate == null ? null : new ECOCreativeCell(delegate, CellConfig.create(stack).size());
        }
    }
}
