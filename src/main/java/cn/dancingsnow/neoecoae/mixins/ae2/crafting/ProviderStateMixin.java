package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch.ECOProviderStateSnapshot;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$ProviderState", remap = false)
public abstract class ProviderStateMixin implements ECOProviderStateSnapshot {
    @Shadow
    @Final
    private ICraftingProvider provider;

    @Shadow
    @Final
    private Set<AEKey> emitableItems;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private int priority;

    @Override
    public ICraftingProvider neoecoae$provider() {
        return provider;
    }

    @Override
    public Set<AEKey> neoecoae$emitableItems() {
        return emitableItems;
    }

    @Override
    public List<IPatternDetails> neoecoae$patterns() {
        return patterns;
    }

    @Override
    public int neoecoae$priority() {
        return priority;
    }
}
