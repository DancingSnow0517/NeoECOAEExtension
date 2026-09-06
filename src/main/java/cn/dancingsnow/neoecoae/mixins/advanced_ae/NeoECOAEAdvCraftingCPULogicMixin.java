package cn.dancingsnow.neoecoae.mixins.advanced_ae;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.compat.advanced_ae.NeoECOAEAdvCraftingFastPathExecutor;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooks AdvancedAE's dynamically-created quantum-computer CPUs into ECO's verified batch path. */
@Pseudo
@Mixin(value = AdvCraftingCPULogic.class, remap = false)
public abstract class NeoECOAEAdvCraftingCPULogicMixin {
    @Unique
    private static final Logger NEOECOAE$LOGGER = LoggerFactory.getLogger("neoecoae");
    @Unique
    private static boolean NEOECOAE$fastPathAccessorsBroken;

    @Shadow @Final AdvCraftingCPU cpu;
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow private ExecutingCraftingJob job;

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void neoecoae$tryFastPath(
            int maxPatterns,
            appeng.me.service.CraftingService craftingService,
            IEnergyService energyService,
            @Nullable Level level,
            CallbackInfoReturnable<Integer> cir) {
        if (NEOECOAE$fastPathAccessorsBroken) {
            return;
        }
        try {
            int pushed = NeoECOAEAdvCraftingFastPathExecutor.execute(
                    this.cpu, this.job, this.inventory, maxPatterns, craftingService, energyService, level);
            if (pushed > 0) {
                cir.setReturnValue(pushed);
            }
        } catch (AbstractMethodError | NoSuchMethodError | ClassCastException failure) {
            // An AdvancedAE update can change or omit an accessor target. Native crafting remains a valid fallback;
            // never let an optional FastPath integration take down the server tick.
            NEOECOAE$fastPathAccessorsBroken = true;
            NEOECOAE$LOGGER.warn("Disabling AdvancedAE FastPath for this crafting pass after an incompatible accessor", failure);
        }
    }

    /** Keeps the existing ECO worker's job-directed output routing for native single-craft fallback. */
    @WrapOperation(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean neoecoae$pushPatternWithJob(
            ICraftingProvider provider,
            appeng.api.crafting.IPatternDetails details,
            KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                && this.job instanceof NeoECOAEAdvCraftingJobAccessor jobAccess) {
            return patternBus.pushPattern(
                    details,
                    inputHolder,
                    jobAccess.neoecoae$getLink().getCraftingID());
        }
        return original.call(provider, details, inputHolder);
    }
}
