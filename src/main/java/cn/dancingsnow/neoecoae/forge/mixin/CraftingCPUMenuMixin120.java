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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.network.NetworkDirection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Bridges ECOCraftingCPU into AE2's {@link CraftingCPUMenu} so the right-side
 * crafting status detail panel shows entries for ECO CPUs.
 */
@SuppressWarnings("removal")
@Mixin(CraftingCPUMenu.class)
public abstract class CraftingCPUMenuMixin120 extends AEBaseMenu {

    public CraftingCPUMenuMixin120(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Final @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Shadow
    private CraftingCPUCluster cpu;

    @Unique
    private ECOCraftingCPU neoecoae$cpu = null;

    @Final @Shadow
    private Consumer<AEKey> cpuChangeListener;

    @Shadow
    public CpuSelectionMode schedulingMode;

    @Shadow
    public boolean cantStoreItems;

    @Unique
    private boolean neoecoae$cachedSuspend = false;

    @Unique
    private ServerPlayer neoecoae$viewer = null;

    @Inject(
        method = {"<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Ljava/lang/Object;)V"},
        at = {@At("TAIL")}
    )
    private void neoecoae$onInit(MenuType<?> menuType, int id, Inventory inv, Object host, CallbackInfo ci) {
        if (inv.player instanceof ServerPlayer sp) {
            this.neoecoae$viewer = sp;
        }
    }

    @Inject(
        method = {"setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V"},
        at = {@At("HEAD")}
    )
    private void neoecoae$onSetCPU(ICraftingCPU c, CallbackInfo ci) {
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
            this.neoecoae$cpu = null;
        }
        if (c instanceof ECOCraftingCPU ecoCPU) {
            this.incrementalUpdateHelper.reset();
            this.neoecoae$cpu = ecoCPU;
            KeyCounter allItems = new KeyCounter();
            this.neoecoae$cpu.getLogic().getAllItems(allItems);
            for (Object2LongMap.Entry<AEKey> entry : allItems) {
                this.incrementalUpdateHelper.addChange(entry.getKey());
            }
            this.neoecoae$cpu.getLogic().addListener(this.cpuChangeListener);
        }
    }

    /**
     * Fully override broadcastChanges for ECO CPUs because AE2's native
     * implementation requires {@code this.cpu != null} (a CraftingCPUCluster),
     * which is always null when an ECO CPU is selected.
     */
    @Inject(method = {"broadcastChanges"}, at = {@At("HEAD")}, cancellable = true)
    public void neoecoae$onBroadcastChanges(CallbackInfo ci) {
        if (this.isServerSide() && this.neoecoae$cpu != null) {
            ECOCraftingCPULogic logic = this.neoecoae$cpu.getLogic();
            this.schedulingMode = this.neoecoae$cpu.getSelectionMode();
            this.cantStoreItems = logic.isCantStoreItems();

            if (this.incrementalUpdateHelper.hasChanges()
                || this.neoecoae$cachedSuspend != logic.isJobSuspended()) {

                CraftingStatus status = neoecoae$createStatus(this.incrementalUpdateHelper, logic);
                this.incrementalUpdateHelper.commitChanges();
                this.neoecoae$cachedSuspend = logic.isJobSuspended();

                // Send via Forge networking to the viewing player
                if (this.neoecoae$viewer != null) {
                    CraftingStatusPacket packet = new CraftingStatusPacket(containerId, status);
                    this.neoecoae$viewer.connection.send(packet.toPacket(NetworkDirection.PLAY_TO_CLIENT));
                }
            }
            super.broadcastChanges();
            ci.cancel();
        }
    }

    @Inject(method = {"cancelCrafting"}, at = {@At("TAIL")})
    public void neoecoae$onCancelCrafting(CallbackInfo ci) {
        if (!this.isClientSide() && this.neoecoae$cpu != null) {
            this.neoecoae$cpu.cancelJob();
        }
    }

    @Inject(method = {"removed"}, at = {@At("TAIL")})
    public void neoecoae$onRemoved(Player player, CallbackInfo ci) {
        this.neoecoae$viewer = null;
        if (this.neoecoae$cpu != null) {
            this.neoecoae$cpu.getLogic().removeListener(this.cpuChangeListener);
        }
    }

    /**
     * Build a {@link CraftingStatus} from ECO CPU logic data.
     */
    @Unique
    private static CraftingStatus neoecoae$createStatus(IncrementalUpdateHelper changes, ECOCraftingCPULogic logic) {
        boolean full = changes.isFullUpdate();
        ImmutableList.Builder<CraftingStatusEntry> entries = ImmutableList.builder();

        for (AEKey what : changes) {
            long storedCount = logic.getStored(what);
            long activeCount = logic.getWaitingFor(what);
            long pendingCount = logic.getPendingOutputs(what);
            AEKey sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }
            CraftingStatusEntry entry = new CraftingStatusEntry(
                changes.getOrAssignSerial(what), sentStack, storedCount, activeCount, pendingCount);
            entries.add(entry);
            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        long elapsedTime = logic.getElapsedTimeTracker().getElapsedTime();
        long remainingItems = logic.getElapsedTimeTracker().getRemainingItemCount();
        long startItems = logic.getElapsedTimeTracker().getStartItemCount();
        return new CraftingStatus(full, elapsedTime, remainingItems, startItems, entries.build());
    }

    @Shadow
    protected void setCPU(ICraftingCPU c) {
    }
}