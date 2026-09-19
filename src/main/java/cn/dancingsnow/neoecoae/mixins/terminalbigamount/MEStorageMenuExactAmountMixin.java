package cn.dancingsnow.neoecoae.mixins.terminalbigamount;

import appeng.menu.me.common.MEStorageMenu;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.network.ECOExactAmountsS2CPacket;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuExactAmountMixin {
    @Unique private Map<appeng.api.stacks.AEKey, ExactAmount> neoecoae$previousExactAmounts;

    @WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE", target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter neoecoae$collectExactAmounts(MEStorage storage, Operation<KeyCounter> original) {
        ExactAmountCollector.begin();
        try {
            KeyCounter result = original.call(storage);
            Map<appeng.api.stacks.AEKey, ExactAmount> current = ExactAmountCollector.finish();
            MEStorageMenu self = (MEStorageMenu) (Object) this;
            if (!current.equals(neoecoae$previousExactAmounts) && self.getPlayer() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new ECOExactAmountsS2CPacket(self.containerId, current));
                neoecoae$previousExactAmounts = current;
            }
            return result;
        } catch (RuntimeException | Error failure) {
            ExactAmountCollector.abort();
            throw failure;
        }
    }
}
