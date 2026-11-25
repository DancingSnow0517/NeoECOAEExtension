package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatusMenu;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

@Mixin(value = CraftingStatusMenu.class, remap = false)
public class CraftingStatusMenuMixin extends CraftingCPUMenu {
    @Shadow private int lastUpdate;
    @Shadow private ICraftingCPU selectedCpu;
    @Shadow private int selectedCpuSerial;

    @WrapOperation(method = {"broadcastChanges()V", "m_38946_()V"}, at = @At(value = "INVOKE",
        target = "Lappeng/menu/me/crafting/CraftingStatusMenu;getGrid()Lappeng/api/networking/IGrid;"))
    private IGrid neoecoae$useCurrentTerminalGrid(CraftingStatusMenu menu, Operation<IGrid> original) {
        var actionHost = getActionHost();
        var node = actionHost == null ? null : actionHost.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    @Inject(method = "setCPU", at = @At("TAIL"))
    private void neoecoae$trackSelectedCpu(@Nullable ICraftingCPU cpu, CallbackInfo ci) {
        selectedCpu = cpu;
        if (cpu == null) selectedCpuSerial = -1;
    }

    @WrapOperation(method = {"broadcastChanges()V", "m_38946_()V"}, at = @At(value = "INVOKE",
        target = "Lappeng/menu/me/crafting/CraftingStatusMenu;createCpuList()Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuList;"))
    private CraftingStatusMenu.CraftingCpuList neoecoae$resetCpuListRefreshTimer(CraftingStatusMenu menu,
            Operation<CraftingStatusMenu.CraftingCpuList> original) {
        var updated = original.call(menu);
        lastUpdate = 0;
        return updated;
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
