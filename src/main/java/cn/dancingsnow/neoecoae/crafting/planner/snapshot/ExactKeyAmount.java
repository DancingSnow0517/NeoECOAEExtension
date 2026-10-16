package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExactCycleAmount;
import cn.dancingsnow.neoecoae.core.network.ExactAmountPacketCodec;
import cn.dancingsnow.neoecoae.core.network.PacketBuf;

/** Packet-safe arbitrary-precision key amount used only by planner diagnostics. */
public record ExactKeyAmount(AEKey key, ExactCycleAmount amount) {
    static ExactKeyAmount read(PacketBuf<AEKey> data) {
        return new ExactKeyAmount(data.readKey(), new ExactCycleAmount(ExactAmountPacketCodec.readSignedInteger(data)));
    }

    void write(PacketBuf<AEKey> data) {
        data.writeKey(key);
        ExactAmountPacketCodec.writeSignedInteger(data, amount.value());
    }
}
