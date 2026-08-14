package cn.dancingsnow.neoecoae.impl.crafting.processingbatch;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;

/** Narrow access contract exposed by the AE2 Pattern Provider Mixin. */
public interface ECOPatternProviderBatchAccess {
    PatternProviderLogicHost neoecoae$getHost();

    IManagedGridNode neoecoae$getMainNode();

    List<IPatternDetails> neoecoae$getPublishedPatterns();

    Set<AEKey> neoecoae$getPatternInputs();

    List<GenericStack> neoecoae$getSendList();

    int neoecoae$getRoundRobinIndex();

    void neoecoae$setRoundRobinIndex(int index);

    void neoecoae$setSendDirection(Direction direction);

    Set<Direction> neoecoae$invokeGetActiveSides();

    @Nullable
    PatternProviderTarget neoecoae$invokeFindAdapter(Direction direction);

    boolean neoecoae$invokeSendStacksOut();

    void neoecoae$invokeOnPushPatternSuccess(IPatternDetails patternDetails);

    default void neoecoae$alertPendingSendList() {
        neoecoae$getMainNode().ifPresent(
                (grid, node) -> grid.getTickManager().alertDevice(node));
    }
}
