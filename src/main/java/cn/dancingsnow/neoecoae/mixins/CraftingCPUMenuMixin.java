package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.CpuSelectionMode;
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
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
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
public class CraftingCPUMenuMixin extends AEBaseMenu {
    public CraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
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
        method = {"<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Ljava/lang/Object;)V"},
        at = {@At("TAIL")}
    )
    private void onInit(MenuType<?> menuType, int id, Inventory ip, Object te, CallbackInfo ci) {
    }

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
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
        }

    }

    @Inject(
        method = {"broadcastChanges"},
        at = {@At("HEAD")}
    )
    public void onBroadcastChanges(CallbackInfo ci) {
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
        long remainingItems = logic.getElapsedTimeTracker().getRemainingItemCount();
        long startItems = logic.getElapsedTimeTracker().getStartItemCount();
        return new CraftingStatus(full, elapsedTime, remainingItems, startItems, newEntries.build());
    }

    @Shadow
    protected void setCPU(ICraftingCPU c) {
    }


}
