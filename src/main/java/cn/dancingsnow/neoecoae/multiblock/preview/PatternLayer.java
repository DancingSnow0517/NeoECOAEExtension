package cn.dancingsnow.neoecoae.multiblock.preview;

import java.util.List;

public record PatternLayer(int y, List<PatternBlockEntry> blocks) {
    public PatternLayer {
        blocks = List.copyOf(blocks);
    }
}
