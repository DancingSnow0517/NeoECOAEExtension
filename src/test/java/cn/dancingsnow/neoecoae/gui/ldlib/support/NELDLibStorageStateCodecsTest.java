package cn.dancingsnow.neoecoae.gui.ldlib.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageInterfaceUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.sync.NEStorageUiStateCodec;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class NELDLibStorageStateCodecsTest {
    @Test
    void storageInterfaceInfiniteImportSettingRoundTrips() {
        var expected = new NEStorageInterfaceUiState(
                new BlockPos(4, 5, 6), true, ECOStorageInterfaceMode.INPUT, 7L, 8L, true, true, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        NELDLibStateCodecs.writeStorageInterface(buffer, expected);

        assertEquals(expected, NELDLibStateCodecs.readStorageInterface(buffer));
    }

    @Test
    void storagePerformanceAndMigrationStateRoundTrips() {
        var expected = new NEStorageUiState(
                new BlockPos(1, 2, 3),
                List.of(),
                List.of(),
                List.of(),
                2,
                4,
                100,
                40L,
                100L,
                123_456L,
                789L,
                true,
                true,
                true,
                true,
                63,
                64,
                false,
                false);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        NEStorageUiStateCodec.write(buffer, expected);
        var actual = NEStorageUiStateCodec.read(buffer);

        assertEquals(expected, actual);
    }

    @Test
    void invalidStoragePageMetadataIsRejectedBeforeWriting() {
        var invalid = new NEStorageUiState(
                BlockPos.ZERO,
                List.of(),
                List.of(),
                List.of(),
                2,
                1,
                0,
                0L,
                0L,
                0L,
                0L,
                false,
                false,
                false,
                false,
                0,
                0,
                true,
                true);

        assertThrows(
                IllegalArgumentException.class,
                () -> NEStorageUiStateCodec.write(new FriendlyByteBuf(Unpooled.buffer()), invalid));
    }
}
