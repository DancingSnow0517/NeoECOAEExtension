package cn.dancingsnow.neoecoae.blocks.entity;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Exposes a (deprecated, but still present in NeoForge 26.1) {@link FluidTank} through the 26.1 Transfer API.
 *
 * <p>main keeps its {@code FluidTank}/{@code LongFluidTank} based logic; this adapter makes those tanks usable as
 * {@code Capabilities.Fluid.BLOCK} handlers. Changes are applied immediately and reverted through a snapshot if the
 * transaction is aborted. The tank is resolved through a supplier on every call so machines whose active tank
 * changes (e.g. the large workstation switching to its hatches) can hand out one stable handler instance.
 */
@SuppressWarnings({"deprecation", "removal"})
public class FluidTankResourceHandler extends SnapshotJournal<FluidTankResourceHandler.Snapshot>
    implements ResourceHandler<FluidResource> {

    record Snapshot(FluidTank tank, FluidStack fluid) {
    }

    private final Supplier<? extends FluidTank> tank;
    private final boolean canInsert;
    private final boolean canExtract;

    public FluidTankResourceHandler(Supplier<? extends FluidTank> tank, boolean canInsert, boolean canExtract) {
        this.tank = Objects.requireNonNull(tank);
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    public FluidTankResourceHandler(FluidTank tank) {
        this(() -> tank, true, true);
    }

    public static FluidTankResourceHandler insertOnly(Supplier<? extends FluidTank> tank) {
        return new FluidTankResourceHandler(tank, true, false);
    }

    public static FluidTankResourceHandler extractOnly(Supplier<? extends FluidTank> tank) {
        return new FluidTankResourceHandler(tank, false, true);
    }

    public FluidTank tank() {
        return tank.get();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return FluidResource.of(tank().getFluid());
    }

    @Override
    public long getAmountAsLong(int index) {
        return tank().getFluidAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tank().getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return !resource.isEmpty() && tank().isFluidValid(resource.toStack(1));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (!canInsert || index != 0 || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidTank t = tank();
        FluidStack stack = resource.toStack(amount);
        int filled = t.fill(stack, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        return t.fill(resource.toStack(filled), IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (!canExtract || index != 0 || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        FluidTank t = tank();
        FluidStack request = resource.toStack(amount);
        FluidStack simulated = t.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) {
            return 0;
        }
        updateSnapshots(transaction);
        return t.drain(resource.toStack(simulated.getAmount()), IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    @Override
    protected Snapshot createSnapshot() {
        FluidTank t = tank();
        return new Snapshot(t, t.getFluid().copy());
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        snapshot.tank().setFluid(snapshot.fluid());
    }
}
