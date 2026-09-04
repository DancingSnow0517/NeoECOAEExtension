package cn.dancingsnow.neoecoae.mixins.sophisticated;

import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOSophisticatedHandlerBridge;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.inventory.FilteredItemHandler", remap = false)
public abstract class FilteredItemHandlerMixin implements ECOSophisticatedHandlerBridge {
    @Shadow @Final protected IItemHandler inventoryHandler;
    @Shadow @Final protected List<FilterLogic> outputFilters;
    @Shadow protected abstract boolean matchesFilters(ItemStack stack, List<FilterLogic> filters);
    @Shadow public abstract int getSlots();

    @Override
    public Object neoecoae$getDelegate() {
        return inventoryHandler;
    }

    @Override
    public boolean neoecoae$isFilteredExtractor() {
        return inventoryHandler instanceof ECOSophisticatedHandlerBridge;
    }

    @Override
    public ItemStack neoecoae$extractMatching(ItemStack requested, boolean simulate) {
        ECOSophisticatedHandlerBridge delegate = (ECOSophisticatedHandlerBridge) inventoryHandler;
        // Filter the actual indexed candidate, just like the slot API does. This matters for component-aware filters.
        ItemStack candidate = delegate.neoecoae$extractMatching(requested, true);
        if (candidate.isEmpty() || !matchesFilters(candidate, outputFilters)) return ItemStack.EMPTY;
        if (simulate) return candidate;
        ItemStack boundedRequest = requested.copyWithCount(Math.min(requested.getCount(), candidate.getCount()));
        return delegate.neoecoae$extractMatching(boundedRequest, false);
    }

    @Override
    public int neoecoae$getSlots() {
        return getSlots();
    }
}
