package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionCountKnowledge;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Cycle members synchronized with the crafting confirmation screen. */
public record ECOCycleItemList(List<Entry> items) implements PacketWritable {
    public static final ECOCycleItemList EMPTY = new ECOCycleItemList(List.of());

    public ECOCycleItemList {
        items = List.copyOf(items);
    }

    public ECOCycleItemList(RegistryFriendlyByteBuf data) {
        this(readFromPacket(data));
    }

    private static List<Entry> readFromPacket(RegistryFriendlyByteBuf data) {
        int size = data.readVarInt();
        List<Entry> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(new Entry(AEKey.readKey(data), readBigInteger(data), readBigInteger(data),
                data.readEnum(ExecutionCountKnowledge.class), data.readEnum(CycleSolveStatus.class), data.readVarInt()));
        }
        return items;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeVarInt(items.size());
        for (Entry item : items) {
            AEKey.writeKey(data, item.what());
            writeBigInteger(data, item.exactSingleNetOutput());
            writeBigInteger(data, item.exactTotalNetOutput());
            data.writeEnum(item.executionCountKnowledge());
            data.writeEnum(item.solveStatus());
            data.writeVarInt(item.componentId());
        }
    }

    public record Entry(AEKey what, BigInteger exactSingleNetOutput, BigInteger exactTotalNetOutput,
            ExecutionCountKnowledge executionCountKnowledge, CycleSolveStatus solveStatus, int componentId) {
        public Entry(AEKey what, long singleNetOutput, long totalNetOutput, int componentId) {
            this(what, BigInteger.valueOf(singleNetOutput), BigInteger.valueOf(totalNetOutput),
                ExecutionCountKnowledge.EXACT, CycleSolveStatus.SUCCESS, componentId);
        }

        /** Compatibility constructor for callers that only have the old three numeric fields. */
        public Entry(AEKey what, long singleNetOutput, long totalNetOutput) {
            this(what, singleNetOutput, totalNetOutput, -1);
        }

        public Entry(AEKey what, long singleNetOutput, long totalNetOutput, boolean known, int componentId) {
            this(what, BigInteger.valueOf(singleNetOutput), BigInteger.valueOf(totalNetOutput),
                known ? ExecutionCountKnowledge.EXACT : ExecutionCountKnowledge.UNKNOWN,
                known ? CycleSolveStatus.SUCCESS : CycleSolveStatus.NOT_IMPLEMENTED, componentId);
        }

        public long singleNetOutput() { return exactSingleNetOutput.longValue(); }
        public long totalNetOutput() { return exactTotalNetOutput.longValue(); }
        public boolean totalNetOutputKnown() { return executionCountKnowledge == ExecutionCountKnowledge.EXACT; }
    }

    private static void writeBigInteger(RegistryFriendlyByteBuf data, BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 4096) throw new IllegalArgumentException("Cycle diagnostic integer is too large");
        data.writeByteArray(bytes);
    }

    private static BigInteger readBigInteger(RegistryFriendlyByteBuf data) {
        byte[] bytes = data.readByteArray(4096);
        if (bytes.length == 0) throw new IllegalArgumentException("Empty cycle diagnostic integer");
        return new BigInteger(bytes);
    }
}
