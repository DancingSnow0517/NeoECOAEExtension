package cn.dancingsnow.neoecoae.mixins.terminalbigamount.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountComparator;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Repo.class)
public abstract class RepoExactAmountMixin {
    @Shadow @Final private ISortSource sortSrc;

    @Inject(method = "getComparator", at = @At("HEAD"), cancellable = true, require = 1)
    private void neoecoae$sortExactAmounts(SortOrder sortOrder, SortDir sortDir,
            CallbackInfoReturnable<Comparator<? super GridInventoryEntry>> cir) {
        if (sortOrder == SortOrder.AMOUNT && sortSrc instanceof MEStorageScreen<?> screen) {
            var comparator = ExactAmountComparator.ascending(screen.getMenu().containerId);
            cir.setReturnValue(sortDir == SortDir.ASCENDING ? comparator : comparator.reversed());
        }
    }
}
