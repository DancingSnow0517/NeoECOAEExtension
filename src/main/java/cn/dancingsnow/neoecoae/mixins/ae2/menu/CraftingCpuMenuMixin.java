package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.network.MapDelta;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.Map;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@SuppressWarnings("removal")
@Mixin(CraftingCPUMenu.class)
public class CraftingCpuMenuMixin extends AEBaseMenu implements cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost {
    @Unique private cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress neoecoae$bigProgress;
    @Unique private int neoecoae$bigSerial = -1;
    @Unique private cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress neoecoae$lastBigProgress;
    @Unique private int neoecoae$lastBigSerial = -2;
    @Unique
    public cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending neoecoae$exactPending =
        new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(java.util.Map.of());
    @Unique  public int neoecoae$exactSerial = -1;
    @Unique  public cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending neoecoae$exactStored =
        new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(java.util.Map.of());
    @Unique  public cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending neoecoae$exactActive =
        new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(java.util.Map.of());

    @Unique private long neoecoae$nextExactTick;
    @Unique private long neoecoae$nextProgressTick;
    @Unique private int neoecoae$sentExactSerial = Integer.MIN_VALUE;
    @Unique private Map<AEKey, ExactAmount> neoecoae$sentStored = Map.of();
    @Unique private Map<AEKey, ExactAmount> neoecoae$sentActive = Map.of();
    @Unique private Map<AEKey, ExactAmount> neoecoae$sentPending = Map.of();

    @Override public void neoecoae$applyExactAmounts(int serial, boolean full,
            MapDelta<AEKey, ExactAmount> stored, MapDelta<AEKey, ExactAmount> active,
            MapDelta<AEKey, ExactAmount> pending) {
        if (!full && serial != neoecoae$exactSerial) return;
        neoecoae$exactStored = new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(ExactMapSync.integers(
            stored.apply(full ? Map.of() : ExactMapSync.finite(neoecoae$exactStored.amounts()))));
        neoecoae$exactActive = new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(ExactMapSync.integers(
            active.apply(full ? Map.of() : ExactMapSync.finite(neoecoae$exactActive.amounts()))));
        neoecoae$exactPending = new cn.dancingsnow.neoecoae.api.me.menu.ECOExactPending(ExactMapSync.integers(
            pending.apply(full ? Map.of() : ExactMapSync.finite(neoecoae$exactPending.amounts()))));
        neoecoae$exactSerial = serial;
    }

    @Override public java.math.BigInteger neoecoae$getExactStored(AEKey key) {
        return (Object) this instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
            && menu.getSelectedCpuSerial() == neoecoae$exactSerial ? neoecoae$exactStored.amounts().get(key) : null;
    }

    @Override public java.math.BigInteger neoecoae$getExactActive(AEKey key) {
        return (Object) this instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
            && menu.getSelectedCpuSerial() == neoecoae$exactSerial ? neoecoae$exactActive.amounts().get(key) : null;
    }

    @Override public java.math.BigInteger neoecoae$getExactPending(AEKey key) {
        return (Object) this instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
            && menu.getSelectedCpuSerial() == neoecoae$exactSerial ? neoecoae$exactPending.amounts().get(key) : null;
    }

    @Override public void neoecoae$setBigOrderProgress(int serial,
            cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress progress) {
        neoecoae$bigSerial = serial;
        neoecoae$bigProgress = progress;
    }
    @Override public cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress neoecoae$getBigOrderProgress() {
        return (Object) this instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
                && menu.getSelectedCpuSerial() == neoecoae$bigSerial ? neoecoae$bigProgress : null;
    }
    @Override public void neoecoae$clearBigOrderProgress() {
        neoecoae$bigProgress = null;
        neoecoae$bigSerial = -1;
    }
    public CraftingCpuMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Final
    @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;
    @Shadow
    private CraftingCPUCluster cpu;
    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;
    @Final
    @Shadow
    private Consumer<AEKey> cpuChangeListener;
    @Shadow
    public CpuSelectionMode schedulingMode;
    @Shadow
    public boolean cantStoreItems;

    @Inject(
        method = {"setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void onSetCPU(ICraftingCPU c, CallbackInfo ci) {
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
        }

        if (c instanceof ECOCraftingCPU ecoCPU) {
            if (this.cpu != null) {
                this.cpu.craftingLogic.removeListener(this.cpuChangeListener);
            }

            this.incrementalUpdateHelper.reset();
            this.neoecoae$cpu = ecoCPU;
            KeyCounter allItems = new KeyCounter();
            this.neoecoae$cpu.getLogic().getAllItems(allItems);

            for (Object2LongMap.Entry<AEKey> entry : allItems) {
                this.incrementalUpdateHelper.addChange(entry.getKey());
            }

            this.neoecoae$cpu.getLogic().addListener(this.cpuChangeListener);
            ci.cancel();
        } else {
            this.neoecoae$cpu = null;
        }

    }

    @Inject(
        method = {"cancelCrafting"},
        at = {@At("TAIL")}
    )
    public void onCancelCrafting(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            this.neoecoae$cpu.cancelJob();
        }

    }

    @Inject(
        method = {"removed"},
        at = {@At("TAIL")}
    )
    public void onRemoved(Player player, CallbackInfo ci) {
        neoecoae$clearBigOrderProgress();
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
        }

    }

    @Inject(
        method = {"broadcastChanges"},
        at = {@At("HEAD")}
    )
    public void onBroadcastChanges(CallbackInfo ci) {
        if (isServerSide() && (Object) this instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
                && getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            var progress = neoecoae$cpu == null ? null : neoecoae$cpu.getProgressView().bigOrder().orElse(null);
            int serial = menu.getSelectedCpuSerial();
            long tick = player.level().getGameTime();
            boolean switched = serial != neoecoae$sentExactSerial;
            if (switched) MenuDataTransport.cancel(player, MenuDataTransport.Channel.CPU);
            if ((switched || tick >= neoecoae$nextExactTick)
                    && !MenuDataTransport.busy(player, MenuDataTransport.Channel.CPU)) {
                neoecoae$nextExactTick = tick + MenuDataTransport.UPDATE_INTERVAL;
                var snapshot = neoecoae$cpu == null ? cn.dancingsnow.neoecoae.network.ExactCpuSnapshot.EMPTY
                    : cn.dancingsnow.neoecoae.network.ExactCpuSnapshot.sample(neoecoae$cpu.getLogic(), tick);
                var stored = snapshot.stored();
                var active = snapshot.active();
                var pending = snapshot.pending();
                var storedDelta = MapDelta.between(switched ? Map.of() : neoecoae$sentStored, stored);
                var activeDelta = MapDelta.between(switched ? Map.of() : neoecoae$sentActive, active);
                var pendingDelta = MapDelta.between(switched ? Map.of() : neoecoae$sentPending, pending);
                if (switched || !storedDelta.isEmpty() || !activeDelta.isEmpty() || !pendingDelta.isEmpty()) {
                    MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> {
                        buf.writeVarInt(serial);
                        buf.writeBoolean(switched);
                        ExactMapSync.write(buf, storedDelta);
                        ExactMapSync.write(buf, activeDelta);
                        ExactMapSync.write(buf, pendingDelta);
                    });
                    neoecoae$sentExactSerial = serial;
                    neoecoae$sentStored = stored;
                    neoecoae$sentActive = active;
                    neoecoae$sentPending = pending;
                }
            }
            boolean stateChanged = progress == null || neoecoae$lastBigProgress == null
                || progress.state() != neoecoae$lastBigProgress.state()
                || !progress.orderId().equals(neoecoae$lastBigProgress.orderId());
            if ((serial != neoecoae$lastBigSerial || stateChanged || tick >= neoecoae$nextProgressTick)
                    && (serial != neoecoae$lastBigSerial || !java.util.Objects.equals(progress, neoecoae$lastBigProgress))) {
                neoecoae$nextProgressTick = tick + MenuDataTransport.UPDATE_INTERVAL;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new cn.dancingsnow.neoecoae.network.ECOBigOrderProgressS2CPacket(containerId, serial, progress));
                neoecoae$lastBigSerial = serial;
                neoecoae$lastBigProgress = progress;
            }
        }
        if (this.isServerSide() && this.neoecoae$cpu != null) {
            this.schedulingMode = this.neoecoae$cpu.getSelectionMode();
            this.cantStoreItems = this.neoecoae$cpu.getLogic().isCantStoreItems();
            if (this.incrementalUpdateHelper.hasChanges()) {
                CraftingStatus status = neoecoae$create(this.incrementalUpdateHelper, this.neoecoae$cpu.getLogic());
                this.incrementalUpdateHelper.commitChanges();
                this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            }
        }

    }

    @Inject(
        method = "toggleScheduling",
        at = @At("TAIL")
    )
    private void onToggleScheduling(CallbackInfo ci) {
        if (!isClientSide() && this.neoecoae$cpu != null) {
            ECOCraftingCPULogic logic = neoecoae$cpu.getLogic();
            logic.setJobSuspended(!logic.isJobSuspended());
        }
    }
    @Unique
    private static CraftingStatus neoecoae$create(IncrementalUpdateHelper changes, ECOCraftingCPULogic logic) {
        boolean full = changes.isFullUpdate();
        ImmutableList.Builder<CraftingStatusEntry> newEntries = ImmutableList.builder();

        for (AEKey what : changes) {
            long storedCount = logic.getStored(what);
            long activeCount = logic.getWaitingFor(what);
            long pendingCount = logic.getPendingOutputs(what);
            AEKey sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            CraftingStatusEntry entry = new CraftingStatusEntry(changes.getOrAssignSerial(what), sentStack, storedCount, activeCount, pendingCount);
            newEntries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        long elapsedTime = logic.getElapsedTimeTracker().getElapsedTime();
        long remainingItems = logic.getElapsedTimeTracker().getSyntheticRemainingItemCount();
        long startItems = logic.getElapsedTimeTracker().getSyntheticStartItemCount();
        boolean suspended = logic.isJobSuspended();
        return new CraftingStatus(full, elapsedTime, remainingItems, startItems, newEntries.build(), suspended);
    }

    @Shadow
    protected void setCPU(ICraftingCPU c) {
    }


}
