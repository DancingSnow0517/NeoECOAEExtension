package cn.dancingsnow.neoecoae.gui.ldlib.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.CpuSelectionMode;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEComputationUiState;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class NELDLibComputationStateCodecsTest {
    @Test
    void computationFastTaskPlanningStateRoundTrips() {
        var expected = new NEComputationUiState(
                new BlockPos(1, 2, 3),
                true,
                true,
                2,
                8,
                true,
                3,
                1,
                64,
                1_024L,
                2_048L,
                3,
                32,
                64,
                48,
                false,
                false,
                true,
                CpuSelectionMode.ANY,
                List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        NELDLibStateCodecs.writeComputation(buffer, expected);

        assertEquals(expected, NELDLibStateCodecs.readComputation(buffer));
    }
}
