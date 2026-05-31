package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.CraftingStatusPacket;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin120 extends AEBaseMenu implements NeoECOCraftingCpuMenuBridge {
    private static final int HEARTBEAT_INTERVAL = 10;

    public CraftingCPUMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;
    @Unique
    private long neoecoae$lastEcoStatusHeartbeatTick = 0;
    @Unique
    private boolean neoecoae$forceEcoStatusUpdate = false;
    @Unique
    private final Set<AEKey> neoecoae$trackedEcoKeys = new HashSet<>();
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

        long tick = TickHandler.instance().getCurrentTick();
        boolean hasJob = logic.hasJob();

        if (this.neoecoae$forceEcoStatusUpdate && !this.incrementalUpdateHelper.hasChanges()) {
            this.neoecoae$queueTrackedEcoKeys();
        }

        if (this.incrementalUpdateHelper.hasChanges() || this.neoecoae$forceEcoStatusUpdate) {
            this.neoecoae$forceEcoStatusUpdate = false;
            CraftingStatus status = neoecoae$createStatus(this.incrementalUpdateHelper, logic, this.neoecoae$trackedEcoKeys);
            this.incrementalUpdateHelper.commitChanges();
            this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            this.neoecoae$lastEcoStatusHeartbeatTick = tick;
            return;
        }

        if (hasJob && !this.neoecoae$trackedEcoKeys.isEmpty()
                && tick - this.neoecoae$lastEcoStatusHeartbeatTick >= HEARTBEAT_INTERVAL) {
            CraftingStatus status = neoecoae$createTrackedStatus(
                    this.incrementalUpdateHelper, logic, this.neoecoae$trackedEcoKeys);
            this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            this.neoecoae$lastEcoStatusHeartbeatTick = tick;
        }
    }

    @Unique
    private void neoecoae$removeEcoListener() {
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.neoecoae$ecoCpuChangeListener);
            this.neoecoae$cpu = null;
        }
        this.neoecoae$trackedEcoKeys.clear();
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
}
