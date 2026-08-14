package cn.dancingsnow.neoecoae.mixins.ae2;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;

import cn.dancingsnow.neoecoae.impl.crafting.processingbatch.ECOPatternProviderBatchAccess;

/** Controlled access surface for AE2's ordinary Pattern Provider dispatch state. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class ECOPatternProviderLogicBatchMixin implements ECOPatternProviderBatchAccess {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Shadow
    @Final
    private Set<AEKey> patternInputs;

    @Shadow
    @Final
    private List<GenericStack> sendList;

    @Shadow
    @Nullable
    private Direction sendDirection;

    @Shadow
    private int roundRobinIndex;

    @Invoker("getActiveSides")
    abstract Set<Direction> neoecoae$invokeGetActiveSidesImpl();

    @Invoker("findAdapter")
    abstract PatternProviderTarget neoecoae$invokeFindAdapterImpl(Direction direction);

    @Invoker("sendStacksOut")
    abstract boolean neoecoae$invokeSendStacksOutImpl();

    @Invoker("onPushPatternSuccess")
    abstract void neoecoae$invokeOnPushPatternSuccessImpl(IPatternDetails patternDetails);

    @Override
    public PatternProviderLogicHost neoecoae$getHost() {
        return host;
    }

    @Override
    public IManagedGridNode neoecoae$getMainNode() {
        return mainNode;
    }

    @Override
    public List<IPatternDetails> neoecoae$getPublishedPatterns() {
        return ((PatternProviderLogic) (Object) this).getAvailablePatterns();
    }

    @Override
    public Set<AEKey> neoecoae$getPatternInputs() {
        return patternInputs;
    }

    @Override
    public List<GenericStack> neoecoae$getSendList() {
        return sendList;
    }

    @Override
    public int neoecoae$getRoundRobinIndex() {
        return roundRobinIndex;
    }

    @Override
    public void neoecoae$setRoundRobinIndex(int index) {
        roundRobinIndex = index;
    }

    @Override
    public void neoecoae$setSendDirection(Direction direction) {
        sendDirection = direction;
    }

    @Override
    public Set<Direction> neoecoae$invokeGetActiveSides() {
        return neoecoae$invokeGetActiveSidesImpl();
    }

    @Override
    @Nullable
    public PatternProviderTarget neoecoae$invokeFindAdapter(Direction direction) {
        return neoecoae$invokeFindAdapterImpl(direction);
    }

    @Override
    public boolean neoecoae$invokeSendStacksOut() {
        return neoecoae$invokeSendStacksOutImpl();
    }

    @Override
    public void neoecoae$invokeOnPushPatternSuccess(IPatternDetails patternDetails) {
        neoecoae$invokeOnPushPatternSuccessImpl(patternDetails);
    }
}
