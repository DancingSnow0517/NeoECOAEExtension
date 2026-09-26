package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleSeedParallelismSyncTest {
    @Test void packetPreservesUnknownSerialAndParallelSeedsAlongsideExactAmounts() {
        AEKey key = mock(AEKey.class);
        var wide = BigInteger.TEN.pow(50);
        var entries = List.of(-1L, 0L, 1L, 64L, Long.MAX_VALUE).stream().map(parallelism ->
            new ECOCycleItemList.Entry(key, wide, wide, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                ExecutionCountKnowledge.EXACT, CycleSolveStatus.SUCCESS, 3, parallelism)).toList();
        try (var statics = mockStatic(AEKey.class)) {
            statics.when(() -> AEKey.writeKey(any(), any())).thenAnswer(call -> {
                ((RegistryFriendlyByteBuf) call.getArgument(0)).writeVarInt(17);
                return null;
            });
            statics.when(() -> AEKey.readKey(any())).thenAnswer(call -> {
                assertEquals(17, ((RegistryFriendlyByteBuf) call.getArgument(0)).readVarInt());
                return key;
            });
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                var expected = new ECOCycleItemList(entries);
                expected.writeToPacket(buffer);
                assertEquals(expected, new ECOCycleItemList(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
    }
}
