package cn.dancingsnow.neoecoae.mixins.terminalbigamount;

import appeng.menu.me.common.MEStorageMenu;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.network.MapDelta;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuExactAmountMixin {
    @Unique private Map<appeng.api.stacks.AEKey, ExactAmount> neoecoae$previousExactAmounts;

    @Unique private long neoecoae$nextExactTick;

    @WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE", target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter neoecoae$collectExactAmounts(MEStorage storage, Operation<KeyCounter> original) {
        MEStorageMenu self = (MEStorageMenu) (Object) this;
        if (!(self.getPlayer() instanceof ServerPlayer player)) return original.call(storage);
        long tick = player.level().getGameTime();
        if (tick < neoecoae$nextExactTick || MenuDataTransport.busy(player, MenuDataTransport.Channel.TERMINAL))
            return original.call(storage);
        neoecoae$nextExactTick = tick + MenuDataTransport.UPDATE_INTERVAL;
        ExactAmountCollector.begin();
        try {
            KeyCounter result = original.call(storage);
            Map<appeng.api.stacks.AEKey, ExactAmount> current = ExactAmountCollector.finish();
            boolean full = neoecoae$previousExactAmounts == null;
            var delta = MapDelta.between(full ? Map.of() : neoecoae$previousExactAmounts, current);
            if (full || !delta.isEmpty()) {
                MenuDataTransport.send(player, MenuDataTransport.Channel.TERMINAL, buf -> {
                    buf.writeBoolean(full);
                    ExactMapSync.write(buf, delta);
                });
                neoecoae$previousExactAmounts = current;
            }
            return result;
        } catch (RuntimeException | Error failure) {
            ExactAmountCollector.abort();
            throw failure;
        }
    }
}
