package cn.dancingsnow.neoecoae.network;

import appeng.core.sync.packets.PatternAccessTerminalPacket;

import java.util.List;

public interface PatternAccessTerminalBatches {
    void neoecoae$setContinuation(List<PatternAccessTerminalPacket> packets);
    List<PatternAccessTerminalPacket> neoecoae$continuation();
}
