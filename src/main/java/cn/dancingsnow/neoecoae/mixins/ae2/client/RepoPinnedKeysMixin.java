package cn.dancingsnow.neoecoae.mixins.ae2.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.PinnedKeys;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECONetworkIdHost;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECONetworkPinnedJobs;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Includes network-scoped crafting pins in the terminal pinned row without leaking across ME networks. */
@Mixin(Repo.class)
public abstract class RepoPinnedKeysMixin {
    @Shadow
    @Final
    private ISortSource sortSrc;

    @Unique
    private long neoecoae$networkId() {
        if (sortSrc instanceof MEStorageScreen<?> screen && screen.getMenu() instanceof ECONetworkIdHost host) {
            return host.neoecoae$getNetworkId();
        }
        return 0L;
    }

    @WrapOperation(
        method = "updateView",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/ArrayList;sort(Ljava/util/Comparator;)V",
            ordinal = 0
        )
    )
    private void neoecoae$sortPinnedRowByNetwork(
            ArrayList<GridInventoryEntry> list,
            Comparator<? super GridInventoryEntry> originalComparator,
            Operation<Void> original) {
        long networkId = neoecoae$networkId();
        if (networkId <= 0L || !ECONetworkPinnedJobs.hasPins(networkId)) {
            original.call(list, originalComparator);
            return;
        }
        Comparator<GridInventoryEntry> scopedComparator = Comparator.comparing(entry -> {
            var networkPin = ECONetworkPinnedJobs.getPinInfo(networkId, entry.getWhat());
            if (networkPin != null) {
                return networkPin.since;
            }
            var globalPin = PinnedKeys.getPinInfo(entry.getWhat());
            return globalPin != null ? globalPin.since : Instant.MAX;
        });
        original.call(list, scopedComparator);
    }

    @ModifyExpressionValue(
        method = "addEntriesToView",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/me/common/PinnedKeys;isEmpty()Z"
        )
    )
    private boolean neoecoae$includeNetworkPins(boolean original) {
        return original && !ECONetworkPinnedJobs.hasPins(neoecoae$networkId());
    }

    @WrapOperation(
        method = "addEntriesToView",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/me/common/PinnedKeys;isPinned(Lappeng/api/stacks/AEKey;)Z"
        )
    )
    private boolean neoecoae$isPinnedInNetwork(AEKey key, Operation<Boolean> original) {
        return original.call(key) || ECONetworkPinnedJobs.isPinned(neoecoae$networkId(), key);
    }
}
