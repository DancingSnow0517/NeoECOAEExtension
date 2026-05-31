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
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
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

import java.util.function.Consumer;

/**
 * Bridges ECOCraftingCPU into AE2's CraftingCPUMenu without injecting inherited
 * AbstractContainerMenu lifecycle methods on this target.
 */
@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin120 extends AEBaseMenu implements NeoECOCraftingCpuMenuBridge {

    public CraftingCPUMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;

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

            this.incrementalUpdateHelper.reset();
            this.neoecoae$cpu = ecoCpu;
            KeyCounter allItems = new KeyCounter();
            ecoCpu.getLogic().getAllItems(allItems);
            for (Object2LongMap.Entry<AEKey> entry : allItems) {
                this.incrementalUpdateHelper.addChange(entry.getKey());
            }
            ecoCpu.getLogic().addListener(this.cpuChangeListener);
            this.neoecoae$broadcastEcoCpuChanges();
            ci.cancel();
        }
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"), require = 0)
    private void neoecoae$onCancelCrafting(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            this.neoecoae$cpu.cancelJob();
        }
    }

    @Inject(method = "toggleScheduling", at = @At("TAIL"), require = 0)
    private void neoecoae$onToggleScheduling(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            ECOCraftingCPULogic logic = this.neoecoae$cpu.getLogic();
            logic.setJobSuspended(!logic.isJobSuspended());
        }
    }

    @Override
    public void neoecoae$cleanupEcoCpuListener() {
        neoecoae$removeEcoListener();
    }

    @Override
    public void neoecoae$broadcastEcoCpuChanges() {
        if (this.isServerSide() && this.neoecoae$cpu != null) {
            this.schedulingMode = this.neoecoae$cpu.getSelectionMode();
            this.cantStoreItems = this.neoecoae$cpu.getLogic().isCantStoreItems();
            if (this.incrementalUpdateHelper.hasChanges()) {
                CraftingStatus status = neoecoae$createStatus(this.incrementalUpdateHelper, this.neoecoae$cpu.getLogic());
                this.incrementalUpdateHelper.commitChanges();
                this.sendPacketToClient(new CraftingStatusPacket(containerId, status));
            }
        }
    }

    @Unique
    private void neoecoae$removeEcoListener() {
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
            this.neoecoae$cpu = null;
        }
    }

    @Unique
    private static CraftingStatus neoecoae$createStatus(IncrementalUpdateHelper changes, ECOCraftingCPULogic logic) {
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

            CraftingStatusEntry entry = new CraftingStatusEntry(
                    changes.getOrAssignSerial(what),
                    sentStack,
                    storedCount,
                    activeCount,
                    pendingCount);
            newEntries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        long elapsedTime = logic.getElapsedTimeTracker().getElapsedTime();
        long remainingItems = logic.getElapsedTimeTracker().getRemainingItemCount();
        long startItems = logic.getElapsedTimeTracker().getStartItemCount();
        return new CraftingStatus(full, elapsedTime, remainingItems, startItems, newEntries.build());
    }
}
