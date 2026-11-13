package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExactCycleAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Solver-free network DTO consumed by the crafting graph client. */
public record CraftingGraphSnapshot(
    int rootNodeId,
    List<MaterialNode> nodes,
    List<PatternNode> patterns,
    List<Edge> edges,
    List<CycleGroup> cycleGroups,
    List<ResourceLocation> fuzzyPlanningItemIds,
    Summary summary
) implements PacketWritable {
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final CraftingGraphSnapshot EMPTY = new CraftingGraphSnapshot(-1, List.of(), List.of(), List.of(),
        List.of(), List.of(), new Summary("EMPTY", 0, 0, 0, 0, 0));

    public CraftingGraphSnapshot {





    }

    public CraftingGraphSnapshot(FriendlyByteBuf data) {
        this(readCompressed(data));
    }

    private CraftingGraphSnapshot(Decoded decoded) {
        this(decoded.rootNodeId, decoded.nodes, decoded.patterns, decoded.edges, decoded.cycleGroups,
            decoded.fuzzyPlanningItemIds, decoded.summary);
    }

    /** The requested root alone is not a material calculation or a zero-byte estimate. */
    public boolean hasMaterialDetails() {
        return nodes.stream().anyMatch(node -> node.fromInventoryBigInteger().signum() > 0
            || node.toCraftBigInteger().signum() > 0 || node.missingBigInteger().signum() > 0);
    }

    /** Shared by the early server summary and the detailed client report. */
    public boolean hasCompleteByteEstimate() {
        boolean materialShortage = summary.planningStatus().equals("MISSING_ITEMS")
            || summary.planningStatus().equals("PLANNED_BUT_AMOUNT_UNREPRESENTABLE");
        return hasMaterialDetails() && cycleGroups.stream().allMatch(cycle ->
            cycle.status().equals("SOLVED") || cycle.status().equals("NOT_REQUIRED")
                || materialShortage && cycle.status().equals("INSUFFICIENT_EXTERNAL_INPUT")
                    && cycle.executionCountKnowledge() == ExecutionCountKnowledge.EXACT);
    }

    /** Exact confirmation rows can arrive before the much larger recipe graph of the same plan revision. */
    public CraftingGraphSnapshot materialReport() {
        return new CraftingGraphSnapshot(rootNodeId, nodes, List.of(), List.of(), cycleGroups.stream()
            .map(cycle -> new CycleGroup(cycle.componentId(), cycle.memberNodeIds(), List.of(), cycle.status(),
                cycle.requiredOutputs(), cycle.externalInputs(), cycle.requiredSeed(), List.of(), List.of(),
                cycle.singleNetOutputs(), cycle.totalNetOutputs(), cycle.availableAmounts(),
                cycle.exactSingleNetOutputs(), cycle.exactTotalNetOutputs(), cycle.executionCountKnowledge(),
                cycle.solveStatus(), Recursion.EMPTY)).toList(), fuzzyPlanningItemIds, summary);
    }

    /** Reuses the graph format, with persistent NBT keys instead of connection-specific registry IDs. */
    public CompoundTag writePersistent() {
        var raw = new PersistentBuffer(Unpooled.buffer(256, MAX_UNCOMPRESSED_BYTES));
        try {
            writeRaw(raw);
            var tag = new CompoundTag();
            tag.putInt("version", 1);
            tag.putInt("size", raw.readableBytes());
            byte[] compressed = compress(raw);
            if (compressed.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Saved crafting graph too large");
            tag.putByteArray("data", compressed);
            return tag;
        } finally {
            raw.release();
        }
    }

    public static CraftingGraphSnapshot readPersistent(CompoundTag tag) {
        int size = tag.getInt("size");
        byte[] bytes = tag.getByteArray("data");
        if (tag.getInt("version") != 1 || size < 0 || size > MAX_UNCOMPRESSED_BYTES
                || bytes.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Invalid saved crafting graph");
        var raw = new PersistentBuffer(Unpooled.wrappedBuffer(decompress(bytes, size)));
        try {
            var graph = new CraftingGraphSnapshot(readRaw(raw));
            if (raw.isReadable()) throw new IllegalArgumentException("Trailing saved crafting graph data");
            return graph;
        } finally {
            raw.release();
        }
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        var raw = new FriendlyByteBuf(Unpooled.buffer(256, MAX_UNCOMPRESSED_BYTES));
        try {
            writeRaw(raw);
            int length = raw.readableBytes();
            byte[] compressed = compress(raw);
            if (compressed.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Graph is too large");
            data.writeVarInt(length);
            data.writeByteArray(compressed);
        } finally {
            raw.release();
        }
    }

    /** Each page is compressed independently and fits the existing queued menu transport. */
    public void writeToPages(PacketWritable trailer, Consumer<Page> output) {
        var raw = new FriendlyByteBuf(Unpooled.buffer(256, MAX_UNCOMPRESSED_BYTES));
        try {
            writeRaw(raw);
            trailer.writeToPacket(raw);
            int offset = 0;
            while (raw.isReadable()) {
                byte[] bytes = new byte[Math.min(Page.MAX_BYTES, raw.readableBytes())];
                raw.readBytes(bytes);
                output.accept(new Page(offset, !raw.isReadable(), bytes));
                offset += bytes.length;
            }
        } finally {
            raw.release();
        }
    }

    /** Reads a fully reassembled report, leaving its cycle-item trailer for the caller. */
    public static CraftingGraphSnapshot readUncompressed(FriendlyByteBuf raw) {
        return new CraftingGraphSnapshot(readRaw(raw));
    }

    private static Decoded readRaw(FriendlyByteBuf raw) {
        return new Decoded(raw.readVarInt(), readList(raw, MaterialNode::read),
            readList(raw, PatternNode::read), readList(raw, Edge::read), readList(raw, CycleGroup::read),
            readList(raw, FriendlyByteBuf::readResourceLocation), Summary.read(raw));
    }

    public record Page(int offset, boolean last, byte[] bytes) implements PacketWritable {
        public static final int MAX_BYTES = 1024 * 1024;

        public Page {
            if (offset < 0 || bytes.length < 1 || bytes.length > MAX_BYTES
                    || (!last && bytes.length != MAX_BYTES)
                    || offset > MAX_UNCOMPRESSED_BYTES - bytes.length) {
                throw new IllegalArgumentException("Invalid crafting report page bounds");
            }
        }

        public Page(FriendlyByteBuf data) {
            this(data.readVarInt(), data.readBoolean(), readBytes(data));
        }

        private static byte[] readBytes(FriendlyByteBuf data) {
            int length = data.readVarInt();
            if (length < 1 || length > MAX_BYTES) {
                throw new IllegalArgumentException("Invalid crafting report page size: " + length);
            }
            return decompress(data.readByteArray(MAX_BYTES * 2), length);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {
            var raw = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                data.writeVarInt(offset);
                data.writeBoolean(last);
                data.writeVarInt(bytes.length);
                data.writeByteArray(compress(raw));
            } finally {
                raw.release();
            }
        }
    }

    private void writeRaw(FriendlyByteBuf data) {
        data.writeVarInt(rootNodeId);
        writeList(data, nodes, MaterialNode::write);
        writeList(data, patterns, PatternNode::write);
        writeList(data, edges, Edge::write);
        writeList(data, cycleGroups, CycleGroup::write);
        writeList(data, fuzzyPlanningItemIds, (itemId, output) -> output.writeResourceLocation(itemId));
        summary.write(data);
    }

    private static Decoded readCompressed(FriendlyByteBuf data) {
        int expectedSize = data.readVarInt();
        if (expectedSize < 0 || expectedSize > MAX_UNCOMPRESSED_BYTES) {
            throw new IllegalArgumentException("Invalid uncompressed graph size: " + expectedSize);
        }
        byte[] compressed = data.readByteArray(MAX_COMPRESSED_BYTES);
        byte[] uncompressed = decompress(compressed, expectedSize);
        var raw = new FriendlyByteBuf(Unpooled.wrappedBuffer(uncompressed));
        try {
            Decoded result = readRaw(raw);
            if (raw.isReadable()) throw new IllegalArgumentException("Trailing bytes in crafting graph snapshot");
            return result;
        } finally {
            raw.release();
        }
    }

    private static byte[] compress(FriendlyByteBuf input) {
        try {
            var output = new ByteArrayOutputStream(Math.max(64, input.readableBytes() / 4));
            var deflater = new Deflater(Deflater.BEST_SPEED);
            try (var stream = new DeflaterOutputStream(output, deflater)) {
                byte[] chunk = new byte[8192];
                while (input.isReadable()) {
                    int count = Math.min(chunk.length, input.readableBytes());
                    input.readBytes(chunk, 0, count);
                    stream.write(chunk, 0, count);
                }
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
            List<CycleGroup> cycleGroups, List<ResourceLocation> fuzzyPlanningItemIds, Summary summary) {}

    public enum MaterialStatus { SATISFIED, CRAFTING, MISSING, UNSUPPORTED, CYCLE }
    public enum CandidateStatus { SELECTED, REJECTED, UNSUPPORTED }
    public enum EdgeKind { PATTERN_OUTPUT, PATTERN_INPUT, BYPRODUCT, CYCLE_INTERNAL }

    public record MaterialNode(int nodeId, AEKey key, long requested, long fromInventory, long toCraft, long missing,
            MaterialStatus status, String exactRequested, String exactFromInventory, String exactToCraft,
            String exactMissing, String exactConsumed, String exactProduced) {
        public MaterialNode(int nodeId, AEKey key, long requested, long fromInventory, long toCraft, long missing,
                MaterialStatus status) {
            this(nodeId, key, requested, fromInventory, toCraft, missing, status, Long.toString(requested),
                Long.toString(fromInventory), Long.toString(toCraft), Long.toString(missing), "0", "0");
        }
        public BigInteger requestedBigInteger() { return new BigInteger(exactRequested); }
        public BigInteger fromInventoryBigInteger() { return new BigInteger(exactFromInventory); }
        public BigInteger toCraftBigInteger() { return new BigInteger(exactToCraft); }
        public BigInteger missingBigInteger() { return new BigInteger(exactMissing); }
        public BigInteger consumedBigInteger() { return new BigInteger(exactConsumed); }
        public BigInteger producedBigInteger() { return new BigInteger(exactProduced); }
        private static MaterialNode read(FriendlyByteBuf data) {
            return new MaterialNode(data.readVarInt(), readKey(data), data.readVarLong(), data.readVarLong(),
                data.readVarLong(), data.readVarLong(), data.readEnum(MaterialStatus.class), data.readUtf(),
                data.readUtf(), data.readUtf(), data.readUtf(), data.readUtf(), data.readUtf());
        }

        private void write(FriendlyByteBuf data) {
            data.writeVarInt(nodeId);
            writeKey(data, key);
            data.writeVarLong(requested);
            data.writeVarLong(fromInventory);
            data.writeVarLong(toCraft);
            data.writeVarLong(missing);
            data.writeEnum(status);
            data.writeUtf(exactRequested);
            data.writeUtf(exactFromInventory);
            data.writeUtf(exactToCraft);
            data.writeUtf(exactMissing);
            data.writeUtf(exactConsumed);
            data.writeUtf(exactProduced);
        }
    }

    public record Relationship(int materialNodeId, PlannerAmount plannerAmount) {
        public Relationship(int materialNodeId, long amount) { this(materialNodeId, PlannerAmount.of(amount)); }
        public long amount() { return plannerAmount.longValueSaturated(); }
        private static Relationship read(FriendlyByteBuf data) {
            return new Relationship(data.readVarInt(), readAmount(data));
        }

        private void write(FriendlyByteBuf data) {
            data.writeVarInt(materialNodeId);
            writeAmount(data, plannerAmount);
        }
    }

    public record PatternNode(int patternNodeId, String displayIdentity, List<Relationship> inputs,
            List<Relationship> outputs,
            PlannerAmount plannerFiringCount, CandidateStatus status, @Nullable String rejectionReason, int componentId) {
        public PatternNode(int patternNodeId, String displayIdentity, List<Relationship> inputs,
                List<Relationship> outputs, long firingCount, CandidateStatus status,
                @Nullable String rejectionReason, int componentId) {
            this(patternNodeId, displayIdentity, inputs, outputs, PlannerAmount.of(firingCount), status,
                rejectionReason, componentId);
        }
        public long firingCount() { return plannerFiringCount.longValueSaturated(); }

        private static PatternNode read(FriendlyByteBuf data) {
            return new PatternNode(data.readVarInt(), data.readUtf(), readList(data, Relationship::read),
                readList(data, Relationship::read), readAmount(data), data.readEnum(CandidateStatus.class),
                readNullableString(data), data.readVarInt());
        }

        private void write(FriendlyByteBuf data) {
            data.writeVarInt(patternNodeId);
            data.writeUtf(displayIdentity);
            writeList(data, inputs, Relationship::write);
            writeList(data, outputs, Relationship::write);
            writeAmount(data, plannerFiringCount);
            data.writeEnum(status);
            writeNullableString(data, rejectionReason);
            data.writeVarInt(componentId);
        }
    }

    /** Edges use globally unique visual IDs: material IDs are non-negative and pattern IDs are encoded by clients. */
    public record Edge(int fromId, int toId, PlannerAmount plannerAmount, EdgeKind kind, boolean selected) {
        public Edge(int fromId, int toId, long amount, EdgeKind kind, boolean selected) {
            this(fromId, toId, PlannerAmount.of(amount), kind, selected);
        }
        public long amount() { return plannerAmount.longValueSaturated(); }
        private static Edge read(FriendlyByteBuf data) {
            return new Edge(data.readVarInt(), data.readVarInt(), readAmount(data), data.readEnum(EdgeKind.class),
                data.readBoolean());
        }

        private void write(FriendlyByteBuf data) {
            data.writeVarInt(fromId);
            data.writeVarInt(toId);
            writeAmount(data, plannerAmount);
            data.writeEnum(kind);
            data.writeBoolean(selected);
        }
    }

    public record KeyAmount(AEKey key, PlannerAmount plannerAmount) {
        public KeyAmount(AEKey key, long amount) { this(key, PlannerAmount.of(amount)); }
        public long amount() { return plannerAmount.longValueSaturated(); }
        private static KeyAmount read(FriendlyByteBuf data) {
            return new KeyAmount(readKey(data), readAmount(data));
        }

        private void write(FriendlyByteBuf data) {
            writeKey(data, key);
            writeAmount(data, plannerAmount);
        }
    }

    public record PatternAmount(int patternNodeId, PlannerAmount plannerAmount) {
        public PatternAmount(int patternNodeId, long amount) { this(patternNodeId, PlannerAmount.of(amount)); }
        public long amount() { return plannerAmount.longValueSaturated(); }
        private static PatternAmount read(FriendlyByteBuf data) {
            return new PatternAmount(data.readVarInt(), readAmount(data));
        }

        private void write(FriendlyByteBuf data) {
            data.writeVarInt(patternNodeId);
            writeAmount(data, plannerAmount);
        }
    }

    public record CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
            List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
            List<PatternAmount> patternTimes, List<Integer> executionWitness, List<KeyAmount> singleNetOutputs,
            List<KeyAmount> totalNetOutputs, List<KeyAmount> availableAmounts,
            List<ExactKeyAmount> exactSingleNetOutputs, List<ExactKeyAmount> exactTotalNetOutputs,
            ExecutionCountKnowledge executionCountKnowledge, CycleSolveStatus solveStatus, Recursion recursion) {
        public CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
                List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
                List<PatternAmount> patternTimes, List<Integer> executionWitness, List<KeyAmount> singleNetOutputs,
                List<KeyAmount> totalNetOutputs, List<KeyAmount> availableAmounts,
                List<ExactKeyAmount> exactSingleNetOutputs, List<ExactKeyAmount> exactTotalNetOutputs,
                ExecutionCountKnowledge executionCountKnowledge, CycleSolveStatus solveStatus) {
            this(componentId, memberNodeIds, internalEdges, status, requiredOutputs, externalInputs, requiredSeed,
                patternTimes, executionWitness, singleNetOutputs, totalNetOutputs, availableAmounts,
                exactSingleNetOutputs, exactTotalNetOutputs, executionCountKnowledge, solveStatus, Recursion.EMPTY);
        }
        /** Compatibility constructor for snapshots written before cycle net-output metadata was added. */
        public CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
                List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
                List<PatternAmount> patternTimes, List<Integer> executionWitness) {
            this(componentId, memberNodeIds, internalEdges, status, requiredOutputs, externalInputs, requiredSeed,
                patternTimes, executionWitness, List.of(), List.of(), List.of(), List.of(), List.of(),
                ExecutionCountKnowledge.UNKNOWN, CycleSolveStatus.NOT_IMPLEMENTED);
        }

        public CycleGroup(int componentId, List<Integer> memberNodeIds, List<Edge> internalEdges, String status,
                List<KeyAmount> requiredOutputs, List<KeyAmount> externalInputs, List<KeyAmount> requiredSeed,
                List<PatternAmount> patternTimes, List<Integer> executionWitness, List<KeyAmount> singleNetOutputs,
                List<KeyAmount> totalNetOutputs, List<KeyAmount> availableAmounts) {
            this(componentId, memberNodeIds, internalEdges, status, requiredOutputs, externalInputs, requiredSeed,
                patternTimes, executionWitness, singleNetOutputs, totalNetOutputs, availableAmounts,
                exact(singleNetOutputs), exact(totalNetOutputs),
                totalNetOutputs.isEmpty() ? ExecutionCountKnowledge.UNKNOWN : ExecutionCountKnowledge.EXACT,
                CycleSolveStatus.NOT_IMPLEMENTED);
        }

        public CycleGroup {












        }

        private static CycleGroup read(FriendlyByteBuf data) {
            return new CycleGroup(data.readVarInt(), readIntList(data), readList(data, Edge::read), data.readUtf(),
                readList(data, KeyAmount::read), readList(data, KeyAmount::read), readList(data, KeyAmount::read),
                readList(data, PatternAmount::read), readIntList(data), readList(data, KeyAmount::read),
                readList(data, KeyAmount::read), readList(data, KeyAmount::read),
                readList(data, ExactKeyAmount::read), readList(data, ExactKeyAmount::read),
                data.readEnum(ExecutionCountKnowledge.class), data.readEnum(CycleSolveStatus.class), Recursion.read(data));
        }

        private void write(FriendlyByteBuf data) {
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
            writeList(data, exactSingleNetOutputs, ExactKeyAmount::write);
            writeList(data, exactTotalNetOutputs, ExactKeyAmount::write);
            data.writeEnum(executionCountKnowledge);
            data.writeEnum(solveStatus);
            recursion.write(data);
        }

        private static List<ExactKeyAmount> exact(List<KeyAmount> values) {
            return values.stream().map(value -> new ExactKeyAmount(value.key(), ExactCycleAmount.of(value.plannerAmount())))
                .toList();
        }
    }

    /** Bounded, exact checkpoints of the selected recipe order, including a conditional material-shortage plan. */
    public record Recursion(List<KeyAmount> initialInventory, List<RecursionLayer> layers, boolean complete) {
        public static final Recursion EMPTY = new Recursion(List.of(), List.of(), false);
        private static Recursion read(FriendlyByteBuf data) {
            return new Recursion(readList(data, KeyAmount::read), readList(data, RecursionLayer::read), data.readBoolean());
        }
        private void write(FriendlyByteBuf data) {
            writeList(data, initialInventory, KeyAmount::write);
            writeList(data, layers, RecursionLayer::write);
            data.writeBoolean(complete);
        }
    }

    public record RecursionLayer(List<KeyAmount> addedInputs, List<KeyAmount> inventory,
            List<PatternAmount> patternTimes, boolean folded) {
        private static RecursionLayer read(FriendlyByteBuf data) {
            return new RecursionLayer(readList(data, KeyAmount::read), readList(data, KeyAmount::read),
                readList(data, PatternAmount::read), data.readBoolean());
        }
        private void write(FriendlyByteBuf data) {
            writeList(data, addedInputs, KeyAmount::write);
            writeList(data, inventory, KeyAmount::write);
            writeList(data, patternTimes, PatternAmount::write);
            data.writeBoolean(folded);
        }
    }

    public record Summary(String planningStatus, int materialNodes, int patternNodes, int edges, int cycleGroups,
            long calculationNanos) {
        private static Summary read(FriendlyByteBuf data) {
            return new Summary(data.readUtf(), data.readVarInt(), data.readVarInt(), data.readVarInt(),
                data.readVarInt(), data.readVarLong());
        }

        private void write(FriendlyByteBuf data) {
            data.writeUtf(planningStatus);
            data.writeVarInt(materialNodes);
            data.writeVarInt(patternNodes);
            data.writeVarInt(edges);
            data.writeVarInt(cycleGroups);
            data.writeVarLong(calculationNanos);
        }
    }

    private static final class PersistentBuffer extends FriendlyByteBuf {

        private PersistentBuffer(io.netty.buffer.ByteBuf buffer) {
            super(buffer);
        }
    }

    private static AEKey readKey(FriendlyByteBuf data) {
        if (!(data instanceof PersistentBuffer)) return ECOKeyFactory.readKey(data);
        var tag = data.readNbt();
        var key = tag == null ? null : ECOKeyFactory.fromTagGeneric(tag);
        if (key == null) throw new IllegalArgumentException("Unavailable key in saved crafting graph");
        return key;
    }

    private static void writeKey(FriendlyByteBuf data, AEKey key) {
        if (data instanceof PersistentBuffer) data.writeNbt(key.toTagGeneric());
        else AEKey.writeKey(data, key);
    }

    private interface Reader<T> { T read(FriendlyByteBuf data); }
    private interface Writer<T> { void write(T value, FriendlyByteBuf data); }

    private static PlannerAmount readAmount(FriendlyByteBuf data) {
        return data.readBoolean() ? PlannerAmount.of(data.readVarLong()) : PlannerAmount.parse(data.readUtf());
    }

    private static void writeAmount(FriendlyByteBuf data, PlannerAmount amount) {
        data.writeBoolean(amount.fitsLong());
        if (amount.fitsLong()) data.writeVarLong(amount.longValueExact());
        else data.writeUtf(amount.toString());
    }

    private static <T> List<T> readList(FriendlyByteBuf data, Reader<T> reader) {
        int size = data.readVarInt();
        if (size < 0 || size > 1_000_000) throw new IllegalArgumentException("Invalid graph list size: " + size);
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(reader.read(data));
        return result;
    }

    private static <T> void writeList(FriendlyByteBuf data, List<T> values, Writer<T> writer) {
        if (values.size() > 1_000_000) throw new IllegalArgumentException("Invalid graph list size: " + values.size());
        data.writeVarInt(values.size());
        for (T value : values) writer.write(value, data);
    }

    private static List<Integer> readIntList(FriendlyByteBuf data) {
        int size = data.readVarInt();
        if (size < 0 || size > 1_000_000) throw new IllegalArgumentException("Invalid graph integer list size");
        List<Integer> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(data.readVarInt());
        return result;
    }

    private static void writeIntList(FriendlyByteBuf data, List<Integer> values) {
        if (values.size() > 1_000_000) throw new IllegalArgumentException("Invalid graph integer list size");
        data.writeVarInt(values.size());
        for (int value : values) data.writeVarInt(value);
    }

    private static @Nullable String readNullableString(FriendlyByteBuf data) {
        return data.readBoolean() ? data.readUtf() : null;
    }

    private static void writeNullableString(FriendlyByteBuf data, @Nullable String value) {
        data.writeBoolean(value != null);
        if (value != null) data.writeUtf(value);
    }
}
