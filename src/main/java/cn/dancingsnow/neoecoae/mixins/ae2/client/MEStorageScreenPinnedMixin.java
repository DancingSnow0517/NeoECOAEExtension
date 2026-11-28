package cn.dancingsnow.neoecoae.mixins.ae2.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import cn.dancingsnow.neoecoae.api.me.menu.ECONetworkIdHost;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECONetworkPinnedJobs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps terminal pinned-row decorations, refreshes, and pruning isolated to the terminal's ME network. */
@Mixin(MEStorageScreen.class)
public abstract class MEStorageScreenPinnedMixin {
    @Shadow
    @Final
    protected Repo repo;

    @Unique
    private long neoecoae$lastNetworkId;

    @Unique
    private long neoecoae$lastPinRevision = -1L;

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void neoecoae$refreshNetworkPinnedRow(CallbackInfo ci) {
        if (((MEStorageScreen<?>) (Object) this).getMenu() instanceof ECONetworkIdHost host) {
            long networkId = host.neoecoae$getNetworkId();
            long revision = ECONetworkPinnedJobs.revision();
            if (networkId != neoecoae$lastNetworkId || revision != neoecoae$lastPinRevision) {
                neoecoae$lastNetworkId = networkId;
                neoecoae$lastPinRevision = revision;
                repo.updateView();
            }
        }
    }

    @WrapOperation(
        method = "renderPinnedRowDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/me/common/PendingCraftingJobs;hasPendingJob(Lappeng/api/stacks/AEKey;)Z"
        )
    )
    private boolean neoecoae$hasPendingJobInNetwork(AEKey what, Operation<Boolean> original) {
        if (((MEStorageScreen<?>) (Object) this).getMenu() instanceof ECONetworkIdHost host) {
            long networkId = host.neoecoae$getNetworkId();
            if (networkId > 0L) {
                return ECONetworkPinnedJobs.hasPendingJob(networkId, what)
                    || (!ECONetworkPinnedJobs.tracksPendingJob(what) && original.call(what));
            }
        }
        return original.call(what);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void neoecoae$markNetworkPinsPrunable(CallbackInfo ci) {
        if (((MEStorageScreen<?>) (Object) this).getMenu() instanceof ECONetworkIdHost host) {
            long networkId = host.neoecoae$getNetworkId();
            if (networkId > 0L) {
                for (var entry : repo.getPinnedEntries()) {
                    ECONetworkPinnedJobs.markPrunable(networkId, entry.getWhat());
                }
            }
        }
    }
}
