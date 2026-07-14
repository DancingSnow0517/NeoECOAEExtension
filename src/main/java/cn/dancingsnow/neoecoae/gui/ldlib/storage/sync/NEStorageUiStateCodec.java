package cn.dancingsnow.neoecoae.gui.ldlib.storage.sync;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageHugeStackState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStoragePaging;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded wire format for the storage host UI snapshot. */
public final class NEStorageUiStateCodec {
    private static final int MAX_TYPES = 64;
    private static final int MAX_DRIVES = 384;
    private static final int MAX_HUGE_STACKS = NEStoragePaging.PAGE_SIZE;
    private static final int MAX_TEXT_LENGTH = 128;

    public static void write(FriendlyByteBuf buf, NEStorageUiState state) {
        validatePageMetadata(state.hugeStackPage(), state.hugeStackPageCount(), state.hugeStackTotalCount());
        buf.writeBlockPos(state.pos());
        buf.writeLong(state.storedEnergy());
        buf.writeLong(state.maxEnergy());
        buf.writeVarLong(Math.max(0L, state.performanceAverageNanos()));
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.infiniteSlotVisible());
        buf.writeBoolean(state.infiniteMode());
        buf.writeBoolean(state.migratingToInfinite());
        buf.writeVarInt(Math.max(0, Math.min(100, state.infiniteMigrationProgress())));
        buf.writeVarInt(Math.max(0, state.infiniteComponentCount()));
        buf.writeBoolean(state.canTakeInfiniteComponent());
        buf.writeBoolean(state.infiniteDomainEmpty());
        buf.writeVarInt(Math.max(0, state.hugeStackPage()));
        buf.writeVarInt(Math.max(1, state.hugeStackPageCount()));
        buf.writeVarInt(Math.max(0, state.hugeStackTotalCount()));

        List<NEStorageUiMatrixState> matrices = state.matrixStates();
        buf.writeVarInt(Math.min(matrices.size(), MAX_DRIVES));
        for (int i = 0; i < Math.min(matrices.size(), MAX_DRIVES); i++) {
            NEStorageUiMatrixState matrix = matrices.get(i);
            buf.writeVarInt(matrix.row());
            buf.writeVarInt(matrix.column());
            buf.writeItem(matrix.stack());
            buf.writeVarInt(matrix.tier());
            buf.writeLong(matrix.usedTypes());
            buf.writeLong(matrix.totalTypes());
            buf.writeLong(matrix.usedBytes());
            buf.writeLong(matrix.totalBytes());
            buf.writeBoolean(matrix.infiniteMember());
        }

        List<NEStorageUiTypeState> types = state.typeStates();
        buf.writeVarInt(Math.min(types.size(), MAX_TYPES));
        for (int i = 0; i < Math.min(types.size(), MAX_TYPES); i++) {
            NEStorageUiTypeState type = types.get(i);
            buf.writeResourceLocation(type.typeId());
            writeBoundedUtf(buf, type.displayName());
            buf.writeLong(type.usedTypes());
            buf.writeLong(type.totalTypes());
            buf.writeLong(type.usedBytes());
            buf.writeLong(type.totalBytes());
            writeHugeAmount(buf, type.safeUsedAmount());
        }

        List<NEStorageHugeStackState> hugeStacks = state.hugeStacks();
        if (hugeStacks.size() > MAX_HUGE_STACKS) {
            throw new IllegalArgumentException("Storage huge-stack page exceeds protocol limit: " + hugeStacks.size());
        }
        buf.writeVarInt(hugeStacks.size());
        for (int i = 0; i < hugeStacks.size(); i++) {
            NEStorageHugeStackState hugeStack = hugeStacks.get(i);
            AEKey.writeKey(buf, hugeStack.key());
            writeHugeAmount(buf, hugeStack.amount());
        }
    }

    public static NEStorageUiState read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long storedEnergy = buf.readLong();
        long maxEnergy = buf.readLong();
        long performanceAverageNanos = buf.readVarLong();
        boolean formed = buf.readBoolean();
        boolean infiniteSlotVisible = buf.readBoolean();
        boolean infiniteMode = buf.readBoolean();
        boolean migratingToInfinite = buf.readBoolean();
        int infiniteMigrationProgress = buf.readVarInt();
        int infiniteComponentCount = buf.readVarInt();
        boolean canTakeInfiniteComponent = buf.readBoolean();
        boolean infiniteDomainEmpty = buf.readBoolean();
        int hugeStackPage = buf.readVarInt();
        int hugeStackPageCount = buf.readVarInt();
        int hugeStackTotalCount = buf.readVarInt();
        validatePageMetadata(hugeStackPage, hugeStackPageCount, hugeStackTotalCount);

        int matrixCount = readCount(buf, MAX_DRIVES, "Storage drive count");
        List<NEStorageUiMatrixState> matrices = new ArrayList<>(matrixCount);
        for (int i = 0; i < matrixCount; i++) {
            matrices.add(new NEStorageUiMatrixState(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readItem(),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readBoolean()));
        }

        int typeCount = readCount(buf, MAX_TYPES, "Storage UI type count");
        List<NEStorageUiTypeState> types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            types.add(new NEStorageUiTypeState(
                    buf.readResourceLocation(),
                    buf.readUtf(MAX_TEXT_LENGTH),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readUtf(MAX_TEXT_LENGTH)));
        }

        int hugeStackCount = readCount(buf, MAX_HUGE_STACKS, "Storage huge stack count");
        List<NEStorageHugeStackState> hugeStacks = new ArrayList<>(hugeStackCount);
        for (int i = 0; i < hugeStackCount; i++) {
            hugeStacks.add(new NEStorageHugeStackState(AEKey.readKey(buf), buf.readUtf(MAX_TEXT_LENGTH)));
        }
        return new NEStorageUiState(
                pos,
                types,
                matrices,
                hugeStacks,
                hugeStackPage,
                hugeStackPageCount,
                hugeStackTotalCount,
                storedEnergy,
                maxEnergy,
                performanceAverageNanos,
                formed,
                infiniteSlotVisible,
                infiniteMode,
                migratingToInfinite,
                infiniteMigrationProgress,
                infiniteComponentCount,
                canTakeInfiniteComponent,
                infiniteDomainEmpty);
    }

    private static int readCount(FriendlyByteBuf buf, int max, String name) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException(name + " exceeds protocol limit: " + count);
        }
        return count;
    }

    private static void validatePageMetadata(int page, int pageCount, int totalCount) {
        int expectedPageCount = totalCount <= 0 ? 1 : (totalCount - 1) / NEStoragePaging.PAGE_SIZE + 1;
        if (page < 0 || pageCount < 1 || page >= pageCount || totalCount < 0 || pageCount != expectedPageCount) {
            throw new IllegalArgumentException(
                    "Invalid storage huge-stack page metadata: " + page + "/" + pageCount + ", total=" + totalCount);
        }
    }

    private static void writeHugeAmount(FriendlyByteBuf buf, String amount) {
        writeBoundedUtf(buf, NELDLibText.compactHugeAmountForSync(amount));
    }

    private static void writeBoundedUtf(FriendlyByteBuf buf, String value) {
        buf.writeUtf(NELDLibText.bounded(value, MAX_TEXT_LENGTH), MAX_TEXT_LENGTH);
    }

    private NEStorageUiStateCodec() {}
}
