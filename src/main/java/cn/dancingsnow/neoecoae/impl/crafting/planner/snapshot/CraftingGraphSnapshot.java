package cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/** Immutable, solver-free network DTO consumed by the crafting graph client. */
public record CraftingGraphSnapshot(
    int rootNodeId,
    List<MaterialNode> nodes,
    List<PatternNode> patterns,
    List<Edge> edges,
    List<CycleGroup> cycleGroups,
    Summary summary
) implements PacketWritable {
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final CraftingGraphSnapshot EMPTY = new CraftingGraphSnapshot(-1, List.of(), List.of(), List.of(),
        List.of(), new Summary("EMPTY", 0, 0, 0, 0, 0));

    public CraftingGraphSnapshot {
        nodes = List.copyOf(nodes);
        patterns = List.copyOf(patterns);
        edges = List.copyOf(edges);
        cycleGroups = List.copyOf(cycleGroups);
    }

    public CraftingGraphSnapshot(RegistryFriendlyByteBuf data) {
        this(readCompressed(data));
    }

    private CraftingGraphSnapshot(Decoded decoded) {
        this(decoded.rootNodeId, decoded.nodes, decoded.patterns, decoded.edges, decoded.cycleGroups, decoded.summary);
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        var raw = new RegistryFriendlyByteBuf(Unpooled.buffer(), data.registryAccess());
        try {
            writeRaw(raw);
            byte[] uncompressed = new byte[raw.readableBytes()];
            raw.getBytes(0, uncompressed);
            data.writeVarInt(uncompressed.length);
            data.writeByteArray(compress(uncompressed));
        } finally {
            raw.release();
        }
    }

    private void writeRaw(RegistryFriendlyByteBuf data) {
        data.writeVarInt(rootNodeId);
        writeList(data, nodes, MaterialNode::write);
        writeList(data, patterns, PatternNode::write);
        writeList(data, edges, Edge::write);
        writeList(data, cycleGroups, CycleGroup::write);
        summary.write(data);
    }

    private static Decoded readCompressed(RegistryFriendlyByteBuf data) {
        int expectedSize = data.readVarInt();
        if (expectedSize < 0 || expectedSize > MAX_UNCOMPRESSED_BYTES) {
            throw new IllegalArgumentException("Invalid uncompressed graph size: " + expectedSize);
        }
        byte[] compressed = data.readByteArray(MAX_COMPRESSED_BYTES);
        byte[] uncompressed = decompress(compressed, expectedSize);
        var raw = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed), data.registryAccess());
        try {
            Decoded result = new Decoded(raw.readVarInt(), readList(raw, MaterialNode::read),
                readList(raw, PatternNode::read), readList(raw, Edge::read), readList(raw, CycleGroup::read),
                Summary.read(raw));
            if (raw.isReadable()) throw new IllegalArgumentException("Trailing bytes in crafting graph snapshot");
            return result;
        } finally {
            raw.release();
        }
    }

    private static byte[] compress(byte[] input) {
        try {
            var output = new ByteArrayOutputStream(Math.max(64, input.length / 4));
            var deflater = new Deflater(Deflater.BEST_SPEED);
            try (var stream = new DeflaterOutputStream(output, deflater)) {
                stream.write(input);
            } finally {
                deflater.end();
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress crafting graph snapshot", e);
        }
    }

    private static byte[] decompress(byte[] input, int expectedSize) {
        try (var stream = new InflaterInputStream(new ByteArrayInputStream(input));
                var output = new ByteArrayOutputStream(expectedSize)) {
            byte[] chunk = new byte[8192];
            int total = 0;
            for (int read; (read = stream.read(chunk)) >= 0;) {
                total += read;
                if (total > expectedSize || total > MAX_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("Crafting graph expands beyond declared size");
                }
                output.write(chunk, 0, read);
            }
            if (total != expectedSize) {
                throw new IllegalArgumentException("Crafting graph size mismatch: expected " + expectedSize
                    + ", got " + total);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to decompress crafting graph snapshot", e);
        }
    }

    private record Decoded(int rootNodeId, List<MaterialNode> nodes, List<PatternNode> patterns, List<Edge> edges,
            List<CycleGroup> cycleGroups, Summary summary) {}

    public enum MaterialStatus { SATISFIED, CRAFTING, MISSING, UNSUPPORTED, CYCLE }
    public enum CandidateStatus { SELECTED, REJECTED, UNSUPPORTED }
    public enum EdgeKind { PATTERN_OUTPUT, PATTERN_INPUT, BYPRODUCT, CYCLE_INTERNAL }

    public record MaterialNode(int nodeId, AEKey key, long requested, long fromInventory, long toCraft, long missing,
            MaterialStatus status) {
        private static MaterialNode read(RegistryFriendlyByteBuf data) {
            return new MaterialNode(data.readVarInt(), AEKey.readKey(data), data.readVarLong(), data.readVarLong(),
                data.readVarLong(), data.readVarLong(), data.readEnum(MaterialStatus.class));
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(nodeId);
            AEKey.writeKey(data, key);
            data.writeVarLong(requested);
            data.writeVarLong(fromInventory);
            data.writeVarLong(toCraft);
            data.writeVarLong(missing);
            data.writeEnum(status);
        }
    }

    public record Relationship(int materialNodeId, long amount) {
        private static Relationship read(RegistryFriendlyByteBuf data) {
            return new Relationship(data.readVarInt(), data.readVarLong());
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(materialNodeId);
            data.writeVarLong(amount);
        }
    }

    public record PatternNode(int patternNodeId, String displayIdentity, List<Relationship> inputs,
            List<Relationship> outputs,
            long firingCount, CandidateStatus status, @Nullable String rejectionReason, int componentId) {
        public PatternNode {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }

        private static PatternNode read(RegistryFriendlyByteBuf data) {
            return new PatternNode(data.readVarInt(), data.readUtf(), readList(data, Relationship::read),
                readList(data, Relationship::read), data.readVarLong(), data.readEnum(CandidateStatus.class),
                readNullableString(data), data.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(patternNodeId);
            data.writeUtf(displayIdentity);
            writeList(data, inputs, Relationship::write);
            writeList(data, outputs, Relationship::write);
            data.writeVarLong(firingCount);
            data.writeEnum(status);
            writeNullableString(data, rejectionReason);
            data.writeVarInt(componentId);
        }
    }

    /** Edges use globally unique visual IDs: material IDs are non-negative and pattern IDs are encoded by clients. */
    public record Edge(int fromId, int toId, long amount, EdgeKind kind, boolean selected) {
        private static Edge read(RegistryFriendlyByteBuf data) {
            return new Edge(data.readVarInt(), data.readVarInt(), data.readVarLong(), data.readEnum(EdgeKind.class),
                data.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(fromId);
            data.writeVarInt(toId);
            data.writeVarLong(amount);
            data.writeEnum(kind);
            data.writeBoolean(selected);
        }
    }

    public record KeyAmount(AEKey key, long amount) {
        private static KeyAmount read(RegistryFriendlyByteBuf data) {
            return new KeyAmount(AEKey.readKey(data), data.readVarLong());
        }

        private void write(RegistryFriendlyByteBuf data) {
            AEKey.writeKey(data, key);
            data.writeVarLong(amount);
        }
    }

    public record PatternAmount(int patternNodeId, long amount) {
        private static PatternAmount read(RegistryFriendlyByteBuf data) {
            return new PatternAmount(data.readVarInt(), data.readVarLong());
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(patternNodeId);
            data.writeVarLong(amount);
        }
    }

    public record CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
            List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
            List<PatternAmount> patternTimes, List<Integer> executionWitness, List<KeyAmount> singleNetOutputs,
            List<KeyAmount> totalNetOutputs, List<KeyAmount> availableAmounts) {
        /** Compatibility constructor for snapshots written before cycle net-output metadata was added. */
        public CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
                List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
                List<PatternAmount> patternTimes, List<Integer> executionWitness) {
            this(componentId, memberNodeIds, internalEdges, status, requiredOutputs, externalInputs, requiredSeed,
                patternTimes, executionWitness, List.of(), List.of(), List.of());
        }

        public CycleGroup {
            memberNodeIds = List.copyOf(memberNodeIds);
            internalEdges = List.copyOf(internalEdges);
            requiredOutputs = List.copyOf(requiredOutputs);
            externalInputs = List.copyOf(externalInputs);
            requiredSeed = List.copyOf(requiredSeed);
            patternTimes = List.copyOf(patternTimes);
            executionWitness = List.copyOf(executionWitness);
            singleNetOutputs = List.copyOf(singleNetOutputs);
            totalNetOutputs = List.copyOf(totalNetOutputs);
            availableAmounts = List.copyOf(availableAmounts);
        }

        private static CycleGroup read(RegistryFriendlyByteBuf data) {
            return new CycleGroup(data.readVarInt(), readIntList(data), readList(data, Edge::read), data.readUtf(),
                readList(data, KeyAmount::read), readList(data, KeyAmount::read), readList(data, KeyAmount::read),
                readList(data, PatternAmount::read), readIntList(data), readList(data, KeyAmount::read),
                readList(data, KeyAmount::read), readList(data, KeyAmount::read));
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeVarInt(componentId);
            writeIntList(data, memberNodeIds);
            writeList(data, internalEdges, Edge::write);
            data.writeUtf(status);
            writeList(data, requiredOutputs, KeyAmount::write);
            writeList(data, externalInputs, KeyAmount::write);
            writeList(data, requiredSeed, KeyAmount::write);
            writeList(data, patternTimes, PatternAmount::write);
            writeIntList(data, executionWitness);
            writeList(data, singleNetOutputs, KeyAmount::write);
            writeList(data, totalNetOutputs, KeyAmount::write);
            writeList(data, availableAmounts, KeyAmount::write);
        }
    }

    public record Summary(String planningStatus, int materialNodes, int patternNodes, int edges, int cycleGroups,
            long calculationNanos) {
        private static Summary read(RegistryFriendlyByteBuf data) {
            return new Summary(data.readUtf(), data.readVarInt(), data.readVarInt(), data.readVarInt(),
                data.readVarInt(), data.readVarLong());
        }

        private void write(RegistryFriendlyByteBuf data) {
            data.writeUtf(planningStatus);
            data.writeVarInt(materialNodes);
            data.writeVarInt(patternNodes);
            data.writeVarInt(edges);
            data.writeVarInt(cycleGroups);
            data.writeVarLong(calculationNanos);
        }
    }

    private interface Reader<T> { T read(RegistryFriendlyByteBuf data); }
    private interface Writer<T> { void write(T value, RegistryFriendlyByteBuf data); }

    private static <T> List<T> readList(RegistryFriendlyByteBuf data, Reader<T> reader) {
        int size = data.readVarInt();
        if (size < 0 || size > 1_000_000) throw new IllegalArgumentException("Invalid graph list size: " + size);
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(reader.read(data));
        return result;
    }

    private static <T> void writeList(RegistryFriendlyByteBuf data, List<T> values, Writer<T> writer) {
        data.writeVarInt(values.size());
        for (T value : values) writer.write(value, data);
    }

    private static List<Integer> readIntList(RegistryFriendlyByteBuf data) {
        int size = data.readVarInt();
        List<Integer> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(data.readVarInt());
        return result;
    }

    private static void writeIntList(RegistryFriendlyByteBuf data, List<Integer> values) {
        data.writeVarInt(values.size());
        for (int value : values) data.writeVarInt(value);
    }

    private static @Nullable String readNullableString(RegistryFriendlyByteBuf data) {
        return data.readBoolean() ? data.readUtf() : null;
    }

    private static void writeNullableString(RegistryFriendlyByteBuf data, @Nullable String value) {
        data.writeBoolean(value != null);
        if (value != null) data.writeUtf(value);
    }
}
