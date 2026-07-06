package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin120 {
    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    private boolean updateList;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Inject(method = "addNode", at = @At("TAIL"))
    private void neoecoae$onAddNode(IGridNode gridNode, net.minecraft.nbt.CompoundTag savedData, CallbackInfo ci) {
        if (NeoECOCraftingServiceBridge.isComputationClusterNode(gridNode)) {
            this.updateList = true;
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void neoecoae$onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (NeoECOCraftingServiceBridge.isComputationClusterNode(gridNode)) {
            this.updateList = true;
        }
    }

    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void neoecoae$onUpdateCPUClusters(CallbackInfo ci) {
        NeoECOCraftingServiceBridge.addRestoredLinks((CraftingService) (Object) this, this.grid);
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void neoecoae$tickComputationCpus(CallbackInfo ci) {
        if (NeoECOCraftingServiceBridge.tickComputationCpus(
                (CraftingService) (Object) this, this.grid, this.energyGrid, this.currentlyCrafting)) {
            this.updateList = true;
        }
    }

    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        cir.setReturnValue(NeoECOCraftingServiceBridge.getCpus(this.grid, cir.getReturnValue()));
    }

    @Inject(
            method = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;"
                    + "Z"
                    + "Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void neoecoae$submitJob(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        this.neoecoae$handleSubmitJob(job, requestingMachine, target, src, cir);
    }

    @Inject(
            method = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;"
                    + "Z"
                    + "Lappeng/api/networking/security/IActionSource;"
                    + "Z)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void neoecoae$submitJobWithSimulationFlag(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            boolean simulate,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        this.neoecoae$handleSubmitJob(job, requestingMachine, target, src, cir);
    }

    private void neoecoae$handleSubmitJob(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        ICraftingSubmitResult result =
                NeoECOCraftingServiceBridge.submitJob(this.grid, job, requestingMachine, target, src);
        if (result != null) {
            if (result.successful()) {
                this.updateList = true;
            }
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "insertIntoCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$insertIntoCpus(AEKey what, long amount, Actionable type, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(
                NeoECOCraftingServiceBridge.insertIntoCpus(this.grid, what, amount, type, cir.getReturnValue()));
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(NeoECOCraftingServiceBridge.getRequestedAmount(this.grid, what, cir.getReturnValue()));
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void neoecoae$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (NeoECOCraftingServiceBridge.hasCpu(this.grid, cpu)) {
            cir.setReturnValue(true);
        }
    }
}
