package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPage;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPageHost;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCpuListClientState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain AE2's renderer and scrollbar, replacing only their full-list assumptions. */
@Mixin(CPUSelectionList.class)
public class CpuSelectionListPagingMixin {
    @Shadow @Final private CraftingStatusMenu menu;
    @Shadow @Final private Scrollbar scrollbar;
    @Unique private final ECOCpuListClientState neoecoae$pages = new ECOCpuListClientState();

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), cancellable = true)
    private void neoecoae$updateCpuPage(CallbackInfo ci) {
        var host = (ECOCpuListPageHost) menu;
        // Publish local input first so a periodic response cannot undo a wheel/drag since the last frame.
        var request = neoecoae$pages.scrollTo(scrollbar.getCurrentScroll());
        if (request != null) host.neoecoae$requestCpuPage(request);
        if (neoecoae$pages.accept(host.neoecoae$getCpuPage())) {
            scrollbar.setRange(0, Math.max(0, neoecoae$pages.visible().total() - ECOCpuListPage.ROWS), 2);
            scrollbar.setCurrentScroll(neoecoae$pages.offset());
            menu.cpuList = new CraftingStatusMenu.CraftingCpuList(neoecoae$pages.visible().cpus());
        }
        scrollbar.setRange(0, Math.max(0, neoecoae$pages.visible().total() - ECOCpuListPage.ROWS), 2);
        ci.cancel();
    }

    @WrapOperation(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
        target = "Ljava/util/List;subList(II)Ljava/util/List;"))
    private List<CraftingStatusMenu.CraftingCpuListEntry> neoecoae$renderCpuPage(
            List<CraftingStatusMenu.CraftingCpuListEntry> ignored, int from, int to, Operation<List> original) {
        return neoecoae$pages.visible().cpus();
    }

    @WrapOperation(method = "hitTestCpu", at = @At(value = "INVOKE",
        target = "Lappeng/client/gui/widgets/Scrollbar;getCurrentScroll()I"))
    private int neoecoae$pageRelativeHit(Scrollbar scrollbar, Operation<Integer> original) {
        return 0;
    }

    @WrapOperation(method = "hitTestCpu", at = @At(value = "INVOKE",
        target = "Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuList;cpus()Ljava/util/List;"))
    private List<CraftingStatusMenu.CraftingCpuListEntry> neoecoae$selectAcknowledgedPage(
            CraftingStatusMenu.CraftingCpuList ignored, Operation<List> original) {
        // A scroll can happen between rendering and mouse-up; don't click the retained old rows.
        return neoecoae$pages.canSelect() && scrollbar.getCurrentScroll() == neoecoae$pages.offset()
            ? neoecoae$pages.visible().cpus() : List.of();
    }
}
