package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExactCycleAmount;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Packet-safe arbitrary-precision key amount used only by planner diagnostics. */
public record ExactKeyAmount(AEKey key, ExactCycleAmount amount) {
    static ExactKeyAmount read(RegistryFriendlyByteBuf data) {
        return new ExactKeyAmount(AEKey.readKey(data), new ExactCycleAmount(ExactMapSync.readSignedInteger(data)));
    }

    void write(RegistryFriendlyByteBuf data) {
        AEKey.writeKey(data, key);
        ExactMapSync.writeSignedInteger(data, amount.value());
    }
}
