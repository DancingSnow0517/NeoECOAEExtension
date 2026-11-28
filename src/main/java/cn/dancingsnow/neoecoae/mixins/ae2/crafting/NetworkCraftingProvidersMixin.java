package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.helpers.NetworkCraftingProviders;
import cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch.ECOProviderStateSnapshot;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = NetworkCraftingProviders.class, remap = false)
public abstract class NetworkCraftingProvidersMixin implements ECOProviderStateSnapshot.Container {
    @Shadow
    @Final
    private Map<IGridNode, ?> craftingProviders;

    @Shadow
    @Final
    private List<?> globalProviders;

    @Override
    public @Nullable ECOProviderStateSnapshot neoecoae$nodeState(@Nullable IGridNode node) {
        return node == null ? null : (ECOProviderStateSnapshot) craftingProviders.get(node);
    }

    @Override
    public @Nullable ECOProviderStateSnapshot neoecoae$globalState(@Nullable ICraftingProvider provider) {
        if (provider == null) {
            return null;
        }
        for (Object state : globalProviders) {
            if (state instanceof ECOProviderStateSnapshot snapshot && snapshot.neoecoae$provider() == provider) {
                return snapshot;
            }
        }
        return null;
    }
}
