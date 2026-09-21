package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.api.me.ECOExactStorageMenu;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.network.BoundedData;
import cn.dancingsnow.neoecoae.network.ECOExactStoragePayload;
import cn.dancingsnow.neoecoae.network.MenuDataSync;
import cn.dancingsnow.neoecoae.network.NetworkMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin implements ECOExactStorageMenu, NetworkMenu {
    @Shadow
    protected abstract boolean canInteractWithGrid();

    @Unique private Map<AEKey, BigInteger> neoecoae$exactAmounts = Map.of();

    @Unique private Map<AEKey, BigInteger> neoecoae$listedAmounts = Map.of();

    @Unique private Map<AEKey, BigInteger> neoecoae$sentAmounts = Map.of();

    @Unique private final MenuDataSync neoecoae$dataSync = new MenuDataSync();

    @Unique private long neoecoae$lastExactTick = Long.MIN_VALUE;

    @Unique private boolean neoecoae$syncFailed;

    @Override
    public MenuDataSync neoecoae$dataSync() {
        return neoecoae$dataSync;
    }

    @Override
    public void neoecoae$resetData() {
        new ECOExactStoragePayload(true, Map.of()).applyToMenu((MEStorageMenu) (Object) this);
    }

    @Override
    public void neoecoae$receiveData(int kind, byte[] data) {
        if (kind != MenuDataSync.EXACT) return;
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            var payload = ECOExactStoragePayload.decode(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing exact inventory bytes");
            payload.applyToMenu((MEStorageMenu) (Object) this);
        } finally {
            buffer.release();
        }
    }

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
        if (next.isEmpty() && !neoecoae$exactAmounts.isEmpty()) {
            // Permission/power loss must cancel an in-flight snapshot, not wait behind it.
            neoecoae$dataSync.invalidate();
            neoecoae$sentAmounts = Map.of();
            neoecoae$syncFailed = false;
        }
        neoecoae$exactAmounts = next;
        neoecoae$dataSync.source(MenuDataSync.EXACT, () -> {
            var snapshot = neoecoae$exactAmounts;
            byte[] data = BoundedData.encode(
                    BoundedData.MAX_BYTES,
                    buf -> ECOExactStoragePayload.encode(new ECOExactStoragePayload(true, snapshot), buf));
            neoecoae$sentAmounts = snapshot;
            return data;
        });
        long tick = player.serverLevel().getGameTime();
        if (!neoecoae$syncFailed
                && neoecoae$dataSync.error().isEmpty()
                && neoecoae$dataSync.subscribed(MenuDataSync.EXACT)
                && !neoecoae$dataSync.busy()
                && (neoecoae$lastExactTick == Long.MIN_VALUE || tick - neoecoae$lastExactTick >= 5)) {
            neoecoae$lastExactTick = tick;
            if (!next.equals(neoecoae$sentAmounts)) {
                var snapshot = next;
                try {
                    var delta = ECOExactStoragePayload.difference(neoecoae$sentAmounts, snapshot);
                    byte[] data =
                            BoundedData.encode(BoundedData.MAX_BYTES, buf -> ECOExactStoragePayload.encode(delta, buf));
                    neoecoae$dataSync.queue(MenuDataSync.EXACT, data, () -> neoecoae$sentAmounts = snapshot);
                } catch (RuntimeException tooLarge) {
                    neoecoae$syncFailed = true;
                    neoecoae$dataSync.fail(player, menu, MenuDataSync.EXACT);
                }
            }
        }
        neoecoae$dataSync.tick(player, menu, MenuDataSync.EXACT);
    }
}
