package cn.dancingsnow.neoecoae.mixins.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountComparator;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Repo.class, remap = false)
public abstract class RepoMixin {
    @Shadow
    @Final
    private ISortSource sortSrc;

    @ModifyReturnValue(
            method = "getComparator(Lappeng/api/config/SortOrder;Lappeng/api/config/SortDir;)Ljava/util/Comparator;",
            at = @At("RETURN"),
            require = 1)
    private Comparator<? super GridInventoryEntry> neoecoae$exactAmountComparator(
            Comparator<? super GridInventoryEntry> original, SortOrder order, SortDir direction) {
        if (order == SortOrder.AMOUNT
                && sortSrc instanceof MEStorageScreen<?> screen
                && screen.getMenu() instanceof ECOExactStorageMenu menu) {
            var amounts = menu.neoecoae$getExactAmounts();
            if (!amounts.isEmpty()) {
                return ExactAmountComparator.create(amounts, direction);
            }
        }
        return original;
    }
}
