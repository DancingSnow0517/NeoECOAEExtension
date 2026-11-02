package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.menu.guisync.GuiSync;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPage;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPageHost;
import com.google.common.collect.ImmutableSet;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftingCpuListEntry;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(CraftingStatusMenu.class)
public class CraftingStatusMenuMixin extends CraftingCPUMenu implements ECOCpuListPageHost {
    @Unique private static final String NEOECOAE_SCROLL_CPU = "neoecoae:scroll_cpu";
    @Unique private static final CraftingStatusMenu.CraftingCpuList NEOECOAE_EMPTY_CPUS =
        new CraftingStatusMenu.CraftingCpuList(java.util.List.of());
    @Shadow public CraftingStatusMenu.CraftingCpuList cpuList;
    @Shadow private int selectedCpuSerial;
    @Shadow private ImmutableSet<ICraftingCPU> lastCpuSet;
    @Unique @GuiSync(113) public ECOCpuListPage neoecoae$cpuPage = ECOCpuListPage.EMPTY;
    @Unique private int neoecoae$cpuOffset;
    @Unique private long neoecoae$cpuScrollSequence;

    @Shadow private CraftingStatusMenu.CraftingCpuList createCpuList() { throw new AssertionError(); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$registerCpuPaging(int id, Inventory inventory, appeng.api.storage.ITerminalHost host,
            CallbackInfo ci) {
        registerClientAction(NEOECOAE_SCROLL_CPU, ScrollRequest.class, this::neoecoae$requestCpuPage);
    }

    @Override public ECOCpuListPage neoecoae$getCpuPage() { return neoecoae$cpuPage; }

    @Override public void neoecoae$requestCpuPage(ScrollRequest request) {
        if (request == null || request.sequence() <= neoecoae$cpuScrollSequence) return;
        if (isClientSide()) {
            sendClientAction(NEOECOAE_SCROLL_CPU, request);
        } else {
            var actionHost = getActionHost();
            var node = actionHost == null ? null : actionHost.getActionableNode();
            if (node == null || node.getGrid() == null) return;
            lastCpuSet = node.getGrid().getCraftingService().getCpus();
            cpuList = createCpuList();
            neoecoae$cpuOffset = Math.clamp(request.offset(), 0,
                Math.max(0, cpuList.cpus().size() - ECOCpuListPage.ROWS));
            neoecoae$cpuScrollSequence = request.sequence();
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void neoecoae$serverOwnsCpuSelection(CallbackInfo ci) {
        if (isClientSide()) {
            // The native fallback selection scans cpuList, which is no longer a complete list on clients.
            super.broadcastChanges();
            ci.cancel();
        }
    }

    @WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE",
        target = "Lappeng/menu/me/crafting/CraftingCPUMenu;broadcastChanges()V"))
    private void neoecoae$syncOnlyCpuPage(CraftingStatusMenu menu, Operation<Void> original) {
        neoecoae$publishCpuPage(() -> original.call(menu));
    }

    @Override public void sendAllDataToRemote() {
        // Vanilla can request a full resync outside broadcastChanges (e.g. container recovery).
        neoecoae$publishCpuPage(super::sendAllDataToRemote);
    }

    @Unique private void neoecoae$publishCpuPage(Runnable publish) {
        var complete = cpuList;
        neoecoae$cpuPage = ECOCpuListPage.sample(complete.cpus(), neoecoae$cpuOffset,
            neoecoae$cpuScrollSequence, selectedCpuSerial);
        neoecoae$cpuOffset = neoecoae$cpuPage.offset();
        // Keep the native list available for selection and integrations, but never serialize it in GuiSync.
        cpuList = NEOECOAE_EMPTY_CPUS;
        try {
            publish.run();
        } finally {
            cpuList = complete;
        }
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "selectCpu", at = @At("HEAD"))
    private void neoecoae$clearParentOnSelection(int serial,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (isClientSide() && (Object) this instanceof cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost host)
            host.neoecoae$clearBigOrderProgress();
    }

    public CraftingStatusMenuMixin(MenuType<?> menuType, int id, Inventory ip, Object te) {
        super(menuType, id, ip, te);
    }

    @WrapOperation(
        method = "createCpuList",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"
        )
    )
    private boolean wrapAdd(ArrayList instance, Object e, Operation<Boolean> original, @Local ICraftingCPU cpu, @Local int serial) {
        ECOCraftingCpuListEntry.of((CraftingStatusMenu.CraftingCpuListEntry) e)
            .neoecoae$setEcoCpu(cpu instanceof ECOCraftingCPU);
        if (cpu instanceof ECOCraftingCPU ecoCPU) {
            IOverlayTextureHolder.of((CraftingStatusMenu.CraftingCpuListEntry) e).neoecoae$setOverlay(ecoCPU.getTier().getCPUOverlayTexture());
        }
        return original.call(instance, e);
    }
}
