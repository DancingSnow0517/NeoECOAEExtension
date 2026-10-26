package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOPatternPushDiagnostics;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.config.NEConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes existing calls only: no extra target probes, insertion, or changes to AE2's return value. */
// INVOKE injectors must have a strictly higher priority than MAE2 1.x's
// priority-1000 overwrite. Injection runs after method merging, against that transport.
@Mixin(value = PatternProviderLogic.class, remap = false, priority = 1100)
public abstract class PatternProviderDiagnosticsMixin implements ECOPatternPushDiagnostics, ECOProcessingTargetProvider {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private List<GenericStack> sendList;
    @Shadow private Direction sendDirection;

    @Unique private final Set<Reason> neoecoae$rejectReasons = EnumSet.noneOf(Reason.class);
    @Unique private boolean neoecoae$observingPush;
    @Unique private boolean neoecoae$foundTarget;
    @Unique private boolean neoecoae$capturingTarget;
    @Unique private Direction neoecoae$targetSide;
    @Unique private Target neoecoae$processingTarget;

    @Override
    public Target neoecoae$getProcessingTarget() { return neoecoae$processingTarget; }

    @Override
    public void neoecoae$clearPushDiagnostics() {
        neoecoae$rejectReasons.clear();
        neoecoae$foundTarget = false;
    }

    @Override
    public Snapshot neoecoae$getPushDiagnostics() {
        var reasons = EnumSet.noneOf(Reason.class);
        reasons.addAll(neoecoae$rejectReasons);
        if (!sendList.isEmpty()) reasons.add(Reason.SEND_LIST_BUSY);
        var be = host.getBlockEntity();
        var level = be.getLevel();
        String location = (level == null ? "unknown" : level.dimension().location().toString())
            + "@" + be.getBlockPos().toShortString();
        return new Snapshot(location, reasons, sendList.size(),
            sendDirection == null ? "none" : sendDirection.getName());
    }

    @WrapMethod(method = "pushPattern")
    private boolean neoecoae$observePush(IPatternDetails pattern, KeyCounter[] inputs,
            Operation<Boolean> original) {
        neoecoae$processingTarget = null;
        neoecoae$targetSide = null;
        neoecoae$capturingTarget = true;
        neoecoae$observingPush = NEConfig.ecoDispatchWatchdogDebug;
        if (neoecoae$observingPush) neoecoae$clearPushDiagnostics();
        try {
            boolean accepted = original.call(pattern, inputs);
            var be = host.getBlockEntity();
            if (accepted && neoecoae$targetSide != null && be.getLevel() != null) {
                neoecoae$processingTarget = new Target(be.getLevel().dimension().location(),
                    be.getBlockPos().relative(neoecoae$targetSide));
            }
            if (neoecoae$observingPush && accepted) neoecoae$rejectReasons.clear();
            else if (neoecoae$observingPush && neoecoae$rejectReasons.isEmpty()) {
                neoecoae$rejectReasons.add(neoecoae$foundTarget ? Reason.UNKNOWN : Reason.NO_EXTERNAL_TARGET);
            }
            return accepted;
        } finally {
            neoecoae$observingPush = false;
            neoecoae$capturingTarget = false;
        }
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean neoecoae$observeSendList(List<?> list, Operation<Boolean> original) {
        boolean empty = original.call(list);
        if (neoecoae$observingPush && list == sendList && !empty) neoecoae$rejectReasons.add(Reason.SEND_LIST_BUSY);
        return empty;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/api/networking/IManagedGridNode;isActive()Z"))
    private boolean neoecoae$observeActive(IManagedGridNode node, Operation<Boolean> original) {
        boolean active = original.call(node);
        if (neoecoae$observingPush && !active) neoecoae$rejectReasons.add(Reason.NODE_INACTIVE);
        return active;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean neoecoae$observePattern(List<?> patterns, Object pattern, Operation<Boolean> original) {
        boolean present = original.call(patterns, pattern);
        if (neoecoae$observingPush && !present) neoecoae$rejectReasons.add(Reason.PATTERN_MISSING);
        return present;
    }

    @Inject(method = "getCraftingLockedReason", at = @At("RETURN"))
    private void neoecoae$observeLock(CallbackInfoReturnable<LockCraftingMode> cir) {
        if (neoecoae$observingPush && cir.getReturnValue() != LockCraftingMode.NONE) {
            neoecoae$rejectReasons.add(Reason.CRAFTING_LOCKED);
        }
    }

    @Inject(method = "findAdapter", at = @At("RETURN"))
    private void neoecoae$observeTarget(Direction side, CallbackInfoReturnable<PatternProviderTarget> cir) {
        if (neoecoae$observingPush && cir.getReturnValue() != null) neoecoae$foundTarget = true;
        if (neoecoae$capturingTarget && cir.getReturnValue() != null) neoecoae$targetSide = side;
    }

    @Inject(method = "adapterAcceptsAll", at = @At("RETURN"))
    private void neoecoae$observeInputRejection(PatternProviderTarget target, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        if (neoecoae$observingPush && !cir.getReturnValue()) {
            neoecoae$rejectReasons.add(Reason.TARGET_REJECTED_INPUT);
        }
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean neoecoae$observeMachine(ICraftingMachine machine, IPatternDetails pattern,
            KeyCounter[] inputs, Direction side, Operation<Boolean> original) {
        boolean accepted = original.call(machine, pattern, inputs, side);
        if (neoecoae$capturingTarget && accepted) neoecoae$targetSide = side.getOpposite();
        if (neoecoae$observingPush) {
            neoecoae$foundTarget = true;
            if (!accepted) neoecoae$rejectReasons.add(Reason.MACHINE_REJECTED);
        }
        return accepted;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"))
    private boolean neoecoae$observeBlocking(PatternProviderTarget target, Set<AEKey> inputs,
            Operation<Boolean> original) {
        boolean blocked = original.call(target, inputs);
        if (neoecoae$observingPush && blocked) neoecoae$rejectReasons.add(Reason.BLOCKING_MODE);
        return blocked;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target =
        "Lappeng/api/crafting/IPatternDetails;supportsPushInputsToExternalInventory()Z"))
    private boolean neoecoae$observeExternalSupport(IPatternDetails pattern, Operation<Boolean> original) {
        boolean supported = original.call(pattern);
        if (neoecoae$observingPush && !supported) neoecoae$rejectReasons.add(Reason.EXTERNAL_INPUT_UNSUPPORTED);
        return supported;
    }
}
