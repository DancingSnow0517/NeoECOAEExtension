package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.core.sync.packets.PatternAccessTerminalPacket;
import cn.dancingsnow.neoecoae.network.PatternAccessTerminalBatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = PatternAccessTerminalPacket.class, remap = false)
public abstract class PatternAccessTerminalPacketMixin implements PatternAccessTerminalBatches {
    @Unique private List<PatternAccessTerminalPacket> neoecoae$continuation = List.of();

    @Override
    public void neoecoae$setContinuation(List<PatternAccessTerminalPacket> packets) {
        neoecoae$continuation = packets;
    }

    @Override
    public List<PatternAccessTerminalPacket> neoecoae$continuation() {
        return neoecoae$continuation;
    }
}
