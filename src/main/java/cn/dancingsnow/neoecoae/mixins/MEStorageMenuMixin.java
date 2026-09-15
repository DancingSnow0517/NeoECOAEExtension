package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOExactInventory;
import cn.dancingsnow.neoecoae.network.ECOExactStoragePayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNetwork;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin implements ECOExactStorageMenu {
    @Shadow
    @Final
    protected MEStorage storage;

    @Shadow
    protected abstract boolean canInteractWithGrid();

    @Unique private Map<AEKey, BigInteger> neoecoae$exactAmounts = Map.of();

    @Unique private int neoecoae$syncTicks;

    @Override
    public Map<AEKey, BigInteger> neoecoae$getExactAmounts() {
        return neoecoae$exactAmounts;
    }

    @Override
    public void neoecoae$setExactAmounts(Map<AEKey, BigInteger> amounts) {
        neoecoae$exactAmounts = Map.copyOf(amounts);
    }

    @Inject(
            method = {"broadcastChanges()V", "m_38946_()V"},
            at = @At("TAIL"),
            require = 1)
    private void neoecoae$syncExactStorage(CallbackInfo ci) {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        if (!(menu.getPlayer() instanceof ServerPlayer player)) return;
        if (neoecoae$syncTicks++ % 10 != 0) return;
        Map<AEKey, BigInteger> next =
                canInteractWithGrid() && menu.isPowered() ? ECOExactInventory.hugeAmounts(storage) : Map.of();
        next = next.entrySet().stream()
                .filter(entry -> menu.isKeyVisible(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!next.equals(neoecoae$exactAmounts)) {
            neoecoae$exactAmounts = next;
            ECOPlannerNetwork.sendToPlayer(player, new ECOExactStoragePayload(menu.containerId, next));
        }
    }
}
