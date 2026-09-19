package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.network.ECOExactStoragePayload;
import cn.dancingsnow.neoecoae.network.ECOPlannerNetwork;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin implements ECOExactStorageMenu {
    @Shadow
    protected abstract boolean canInteractWithGrid();

    @Unique private Map<AEKey, BigInteger> neoecoae$exactAmounts = Map.of();

    @Unique private Map<AEKey, BigInteger> neoecoae$listedAmounts = Map.of();

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
            at = @At("HEAD"),
            require = 1)
    private void neoecoae$beginListing(CallbackInfo ci) {
        neoecoae$listedAmounts = Map.of();
    }

    @WrapOperation(
            method = {"broadcastChanges()V", "m_38946_()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"),
            require = 1,
            allow = 1)
    private KeyCounter neoecoae$collectExact(MEStorage storage, Operation<KeyCounter> original) {
        var listing = ExactAmountCollector.collect(storage, () -> original.call(storage));
        neoecoae$listedAmounts = listing.amounts();
        return listing.stacks();
    }

    @Inject(
            method = {"broadcastChanges()V", "m_38946_()V"},
            at = @At("TAIL"),
            require = 1)
    private void neoecoae$syncExactStorage(CallbackInfo ci) {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        if (!(menu.getPlayer() instanceof ServerPlayer player)) return;
        Map<AEKey, BigInteger> next = canInteractWithGrid() && menu.isPowered() ? neoecoae$listedAmounts : Map.of();
        next = next.entrySet().stream()
                .filter(entry -> menu.isKeyVisible(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!next.equals(neoecoae$exactAmounts)) {
            neoecoae$exactAmounts = next;
            ECOPlannerNetwork.sendToPlayer(player, new ECOExactStoragePayload(menu.containerId, next));
        }
    }
}
