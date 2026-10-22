package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiBlockDefinitionLimitTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void aDefinitionCreatedBeforeWorldLoadFollowsLaterServerLimits() {
        AtomicInteger configuredLength = new AtomicInteger(15);
        MultiBlockDefinition definition = MultiBlockDefinition.builder(Holder.direct(Blocks.STONE))
            .expandMax(() -> configuredLength.get() - 4)
            .create(ignored -> {});

        assertEquals(11, definition.getExpandMax());
        configuredLength.set(9);
        assertEquals(5, definition.getExpandMax());
        configuredLength.set(20);
        assertEquals(16, definition.getExpandMax());
    }

    @Test
    void fixedLimitsKeepTheExistingConstructorAndBuilderContracts() {
        MultiBlockDefinition constructed = new MultiBlockDefinition(List.of(), Holder.direct(Blocks.STONE),
            Component.literal("fixed"), 1, 7, (pos, level) -> {});
        MultiBlockDefinition built = MultiBlockDefinition.builder(Holder.direct(Blocks.STONE))
            .expandMax(8).create(ignored -> {});

        assertEquals(7, constructed.getExpandMax());
        assertEquals(8, built.getExpandMax());
    }
}
