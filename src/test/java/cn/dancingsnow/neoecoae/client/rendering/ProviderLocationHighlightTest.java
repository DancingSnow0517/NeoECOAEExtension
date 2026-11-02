package cn.dancingsnow.neoecoae.client.rendering;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ProviderLocationHighlightTest {
    @Test void overlaysRetainProviderPositionsDimensionSideAndExpireTogether() {
        var position = new BlockPos(918_527, 64, -162_837);
        var source = new CompoundTag();
        source.putLong("position", position.asLong());
        source.putString("dimension", "minecraft:the_nether");
        source.putString("side", "north");
        long start = System.currentTimeMillis();
        ProviderLocationHighlight.show(source);
        source.putLong("position", position.offset(2, 0, 0).asLong());
        ProviderLocationHighlight.show(source);
        var highlights = ProviderLocationHighlight.active(Level.NETHER, start);
        assertEquals(2, highlights.size());
        var highlight = highlights.stream().filter(data -> data.position().equals(position)).findFirst().orElseThrow();
        assertEquals(Direction.NORTH, highlight.side());
        assertTrue(ProviderLocationHighlight.active(Level.OVERWORLD, start).isEmpty());
        assertEquals(2, ProviderLocationHighlight.active(Level.NETHER, start + 7_999).size());
        assertTrue(ProviderLocationHighlight.active(Level.NETHER, System.currentTimeMillis() + 8_001).isEmpty());
    }
}
