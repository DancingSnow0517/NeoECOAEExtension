package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import appeng.hooks.ticking.TickHandler;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingCpuMenuBridge;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin120 extends AEBaseMenu implements NeoECOCraftingCpuMenuBridge {
    @Unique
    private static final long NEOECOAE_ECO_STATUS_UPDATE_INTERVAL = 5L;

    public CraftingCPUMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;
    @Unique
    private boolean neoecoae$forceEcoStatusUpdate = false;
    @Unique
    private final Set<AEKey> neoecoae$trackedEcoKeys = new HashSet<>();
    @Unique
    private final Map<AEKey, NeoEcoEntrySnapshot> neoecoae$lastEcoEntrySnapshots = new HashMap<>();
    @Unique
    private long neoecoae$lastEcoElapsedTime = Long.MIN_VALUE;
    @Unique
    private long neoecoae$lastEcoRemainingItems = Long.MIN_VALUE;
    @Unique
    private long neoecoae$lastEcoStartItems = Long.MIN_VALUE;
    @Unique
    private long neoecoae$lastEcoStatusRevision = Long.MIN_VALUE;
    @Unique
    private boolean neoecoae$lastEcoJobPresent = false;
    @Unique
    private boolean neoecoae$lastEcoSuspended = false;
    @Unique
    private boolean neoecoae$lastEcoCantStoreItems = false;
    @Unique
    private long neoecoae$lastEcoUpdateTick = Long.MIN_VALUE;
    @Unique
    private final Consumer<AEKey> neoecoae$ecoCpuChangeListener = key -> {
        if (key != null) {
            this.incrementalUpdateHelper.addChange(key);
            this.neoecoae$trackedEcoKeys.add(key);
            this.neoecoae$forceEcoStatusUpdate = true;
        }
    };

    @Final
    @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;
    @Shadow
    private CraftingCPUCluster cpu;
    @Final
    @Shadow
    private Consumer<AEKey> cpuChangeListener;
    @Shadow
    public CpuSelectionMode schedulingMode;
    @Shadow
    public boolean cantStoreItems;

    @Inject(method = "setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoecoae$onSetCPU(ICraftingCPU selectedCpu, CallbackInfo ci) {
        neoecoae$removeEcoListener();

        if (selectedCpu instanceof ECOCraftingCPU ecoCpu) {
            if (this.cpu != null) {
                this.cpu.craftingLogic.removeListener(this.cpuChangeListener);
            }
            this.cpu = null;
            this.incrementalUpdateHelper.reset();
            this.neoecoae$trackedEcoKeys.clear();
            this.neoecoae$lastEcoEntrySnapshots.clear();
            this.neoecoae$resetEcoHeaderSnapshot();
            this.neoecoae$resetEcoStatusSnapshot();
            this.neoecoae$cpu = ecoCpu;

            KeyCounter allItems = new KeyCounter();
            ecoCpu.getLogic().getAllItems(allItems);
            for (Object2LongMap.Entry<AEKey> entry : allItems) {
                this.incrementalUpdateHelper.addChange(entry.getKey());
                this.neoecoae$trackedEcoKeys.add(entry.getKey());
            }

            ecoCpu.getLogic().addListener(this.neoecoae$ecoCpuChangeListener);
            this.neoecoae$forceEcoStatusUpdate = true;
            this.neoecoae$broadcastEcoCpuChanges();
            ci.cancel();
        }
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"), require = 0)
    private void neoecoae$onCancelCrafting(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            this.neoecoae$cpu.cancelJob();
            this.neoecoae$trackAllKnownEcoKeys();
            this.neoecoae$forceEcoStatusUpdate = true;
        }
    }

    @Inject(method = "toggleScheduling", at = @At("TAIL"), require = 0)
    private void neoecoae$onToggleScheduling(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            ECOCraftingCPULogic logic = this.neoecoae$cpu.getLogic();
            logic.setJobSuspended(!logic.isJobSuspended());
            this.neoecoae$trackAllKnownEcoKeys();
            this.neoecoae$forceEcoStatusUpdate = true;
        }
    }

    @Override
    public void neoecoae$cleanupEcoCpuListener() {
        neoecoae$removeEcoListener();
    }

    @Override
    public void neoecoae$broadcastEcoCpuChanges() {
        if (!this.isServerSide() || this.neoecoae$cpu == null) {
            return;
        }

        ECOCraftingCPULogic logic = this.neoecoae$cpu.getLogic();
        this.schedulingMode = this.neoecoae$cpu.getSelectionMode();
        this.cantStoreItems = logic.isCantStoreItems();

        boolean hasJob = logic.hasJob();
        boolean suspended = logic.isJobSuspended();
        boolean cantStore = logic.isCantStoreItems();
        long revision = logic.getStatusRevision();
        long currentTick = TickHandler.instance().getCurrentTick();
        boolean jobPresenceChanged = hasJob != this.neoecoae$lastEcoJobPresent;
        boolean statusStateChanged = revision != this.neoecoae$lastEcoStatusRevision
                || suspended != this.neoecoae$lastEcoSuspended
                || cantStore != this.neoecoae$lastEcoCantStoreItems
                || jobPresenceChanged;
        boolean periodicRefresh = hasJob
                && (this.neoecoae$lastEcoUpdateTick == Long.MIN_VALUE
                || currentTick - this.neoecoae$lastEcoUpdateTick >= NEOECOAE_ECO_STATUS_UPDATE_INTERVAL);
        boolean finishedJob = this.neoecoae$lastEcoJobPresent && !hasJob;

        if (hasJob || !this.neoecoae$trackedEcoKeys.isEmpty()) {
            this.neoecoae$queueDynamicEcoStatusChanges(logic);
        } else {
            this.neoecoae$lastEcoEntrySnapshots.clear();
            this.neoecoae$resetEcoHeaderSnapshot();
        }

        if (this.neoecoae$forceEcoStatusUpdate && !this.incrementalUpdateHelper.hasChanges()) {
            this.neoecoae$queueTrackedEcoKeys();
        }

        if (statusStateChanged || periodicRefresh || finishedJob) {
            this.neoecoae$queueTrackedEcoKeys();
            this.neoecoae$queueAllCurrentEcoKeys(logic);
            this.neoecoae$forceEcoStatusUpdate = true;
        }

        if (this.incrementalUpdateHelper.hasChanges() || this.neoecoae$forceEcoStatusUpdate) {
            this.neoecoae$forceEcoStatusUpdate = false;
            CraftingStatus status = neoecoae$createStatus(this.incrementalUpdateHelper, logic, this.neoecoae$trackedEcoKeys);
            this.incrementalUpdateHelper.commitChanges();
            this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            this.neoecoae$rememberEcoHeader(status);
            this.neoecoae$rememberEcoStatus(logic, currentTick);
            return;
        }

        if (hasJob && this.neoecoae$hasEcoHeaderChanged(logic)) {
            CraftingStatus status = neoecoae$createHeaderOnlyStatus(logic);
            this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            this.neoecoae$rememberEcoHeader(status);
            this.neoecoae$rememberEcoStatus(logic, currentTick);
        }
    }

    @Unique
    private void neoecoae$removeEcoListener() {
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.neoecoae$ecoCpuChangeListener);
            this.neoecoae$cpu = null;
        }
        this.neoecoae$trackedEcoKeys.clear();
        this.neoecoae$lastEcoEntrySnapshots.clear();
        this.neoecoae$resetEcoHeaderSnapshot();
        this.neoecoae$resetEcoStatusSnapshot();
        this.neoecoae$forceEcoStatusUpdate = false;
    }

    @Unique
    private void neoecoae$trackAllKnownEcoKeys() {
        if (this.neoecoae$cpu == null) {
            return;
        }
        KeyCounter allItems = new KeyCounter();
        this.neoecoae$cpu.getLogic().getAllItems(allItems);
        for (Object2LongMap.Entry<AEKey> entry : allItems) {
            this.neoecoae$trackedEcoKeys.add(entry.getKey());
        }
    }

    @Unique
    private void neoecoae$queueTrackedEcoKeys() {
        for (AEKey key : this.neoecoae$trackedEcoKeys) {
            this.incrementalUpdateHelper.addChange(key);
        }
    }

    @Unique
    private void neoecoae$queueAllCurrentEcoKeys(ECOCraftingCPULogic logic) {
        KeyCounter allItems = new KeyCounter();
        logic.getAllItems(allItems);
        for (Object2LongMap.Entry<AEKey> entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
            this.neoecoae$trackedEcoKeys.add(entry.getKey());
        }
    }

    @Unique
    private void neoecoae$queueDynamicEcoStatusChanges(ECOCraftingCPULogic logic) {
        KeyCounter allItems = new KeyCounter();
        logic.getAllItems(allItems);
        Set<AEKey> keys = new HashSet<>(this.neoecoae$trackedEcoKeys);
        for (Object2LongMap.Entry<AEKey> entry : allItems) {
            keys.add(entry.getKey());
        }

        for (AEKey key : keys) {
            NeoEcoEntrySnapshot current = NeoEcoEntrySnapshot.of(logic, key);
            NeoEcoEntrySnapshot previous = this.neoecoae$lastEcoEntrySnapshots.get(key);
            if (!current.equals(previous)) {
                this.incrementalUpdateHelper.addChange(key);
                this.neoecoae$trackedEcoKeys.add(key);
                this.neoecoae$lastEcoEntrySnapshots.put(key, current);
            }
        }
    }

    @Unique
    private boolean neoecoae$hasEcoHeaderChanged(ECOCraftingCPULogic logic) {
        long elapsedTime = logic.getElapsedTimeTracker().getElapsedTime();
        long remainingItems = logic.getElapsedTimeTracker().getRemainingItemCount();
        long startItems = logic.getElapsedTimeTracker().getStartItemCount();
        return elapsedTime != this.neoecoae$lastEcoElapsedTime
                || remainingItems != this.neoecoae$lastEcoRemainingItems
                || startItems != this.neoecoae$lastEcoStartItems;
    }

    @Unique
    private void neoecoae$rememberEcoHeader(CraftingStatus status) {
        this.neoecoae$lastEcoElapsedTime = status.getElapsedTime();
        this.neoecoae$lastEcoRemainingItems = status.getRemainingItemCount();
        this.neoecoae$lastEcoStartItems = status.getStartItemCount();
    }

    @Unique
    private void neoecoae$resetEcoHeaderSnapshot() {
        this.neoecoae$lastEcoElapsedTime = Long.MIN_VALUE;
        this.neoecoae$lastEcoRemainingItems = Long.MIN_VALUE;
        this.neoecoae$lastEcoStartItems = Long.MIN_VALUE;
    }

    @Unique
    private void neoecoae$rememberEcoStatus(ECOCraftingCPULogic logic, long currentTick) {
        this.neoecoae$lastEcoStatusRevision = logic.getStatusRevision();
        this.neoecoae$lastEcoJobPresent = logic.hasJob();
        this.neoecoae$lastEcoSuspended = logic.isJobSuspended();
        this.neoecoae$lastEcoCantStoreItems = logic.isCantStoreItems();
        this.neoecoae$lastEcoUpdateTick = currentTick;
    }

    @Unique
    private void neoecoae$resetEcoStatusSnapshot() {
        this.neoecoae$lastEcoStatusRevision = Long.MIN_VALUE;
        this.neoecoae$lastEcoJobPresent = false;
        this.neoecoae$lastEcoSuspended = false;
        this.neoecoae$lastEcoCantStoreItems = false;
        this.neoecoae$lastEcoUpdateTick = Long.MIN_VALUE;
    }

    @Unique
    private static CraftingStatus neoecoae$createStatus(
            IncrementalUpdateHelper changes,
            ECOCraftingCPULogic logic,
            Set<AEKey> trackedKeys) {
        boolean full = changes.isFullUpdate();
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();
        ArrayList<AEKey> deletedKeys = new ArrayList<>();

        for (AEKey what : changes) {
            CraftingStatusEntry entry = neoecoae$createEntry(changes, logic, what, full);
            entries.add(entry);
            trackedKeys.add(what);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
                deletedKeys.add(what);
            }
        }

        trackedKeys.removeAll(deletedKeys);
        return new CraftingStatus(
                full,
                logic.getElapsedTimeTracker().getElapsedTime(),
                logic.getElapsedTimeTracker().getRemainingItemCount(),
                logic.getElapsedTimeTracker().getStartItemCount(),
                entries.build());
    }

    @Unique
    private static CraftingStatus neoecoae$createTrackedStatus(
            IncrementalUpdateHelper changes,
            ECOCraftingCPULogic logic,
            Set<AEKey> trackedKeys) {
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();
        ArrayList<AEKey> deletedKeys = new ArrayList<>();

        for (AEKey what : new ArrayList<>(trackedKeys)) {
            CraftingStatusEntry entry = neoecoae$createEntry(changes, logic, what, false);
            entries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
                deletedKeys.add(what);
            }
        }

        trackedKeys.removeAll(deletedKeys);
        return new CraftingStatus(
                false,
                logic.getElapsedTimeTracker().getElapsedTime(),
                logic.getElapsedTimeTracker().getRemainingItemCount(),
                logic.getElapsedTimeTracker().getStartItemCount(),
                entries.build());
    }

    @Unique
    private static CraftingStatus neoecoae$createHeaderOnlyStatus(ECOCraftingCPULogic logic) {
        return new CraftingStatus(
                false,
                logic.getElapsedTimeTracker().getElapsedTime(),
                logic.getElapsedTimeTracker().getRemainingItemCount(),
                logic.getElapsedTimeTracker().getStartItemCount(),
                ImmutableList.of());
    }

    @Unique
    private static CraftingStatusEntry neoecoae$createEntry(
            IncrementalUpdateHelper changes,
            ECOCraftingCPULogic logic,
            AEKey what,
            boolean full) {
        long storedCount = logic.getStored(what);
        long activeCount = logic.getWaitingFor(what);
        long pendingCount = logic.getPendingOutputs(what);
        AEKey sentStack = what;
        if (!full && changes.getSerial(what) != null) {
            sentStack = null;
        }
        return new CraftingStatusEntry(
                changes.getOrAssignSerial(what),
                sentStack,
                storedCount,
                activeCount,
                pendingCount);
    }

    @Unique
    private record NeoEcoEntrySnapshot(long storedAmount, long activeAmount, long pendingAmount) {
        private static NeoEcoEntrySnapshot of(ECOCraftingCPULogic logic, AEKey what) {
            return new NeoEcoEntrySnapshot(
                    logic.getStored(what),
                    logic.getWaitingFor(what),
                    logic.getPendingOutputs(what));
        }
    }
}
