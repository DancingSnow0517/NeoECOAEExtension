package cn.dancingsnow.neoecoae.network;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** Copy/literal delta against the last completed snapshot. Matches survive variable-length field shifts. */
public final class StateDelta {
    public static final int MAX_STATE_BYTES = 8 * 1024 * 1024;
    private static final int BLOCK = 32;

    public static byte[] encode(byte[] before, byte[] after) {
        if (before.length > MAX_STATE_BYTES || after.length > MAX_STATE_BYTES)
            throw new IllegalArgumentException("UI state exceeds budget");
        Map<Integer, Integer> blocks = new HashMap<>();
        for (int i = 0; i + BLOCK <= before.length; i += BLOCK) blocks.putIfAbsent(hash(before, i), i);
        return BoundedData.encode(BoundedData.MAX_BYTES, out -> {
            out.writeVarInt(after.length);
            int literalStart = 0;
            int cursor = 0;
            while (cursor + BLOCK <= after.length) {
                Integer match = blocks.get(hash(after, cursor));
                if (match == null || !Arrays.equals(before, match, match + BLOCK, after, cursor, cursor + BLOCK)) {
                    cursor++;
                    continue;
                }
                if (literalStart < cursor) writeLiteral(out, after, literalStart, cursor);
                int length = BLOCK;
                while (match + length < before.length
                        && cursor + length < after.length
                        && before[match + length] == after[cursor + length]) length++;
                out.writeBoolean(true);
                out.writeVarInt(length);
                out.writeVarInt(match);
                cursor += length;
                literalStart = cursor;
            }
            if (literalStart < after.length) writeLiteral(out, after, literalStart, after.length);
        });
    }

    public static byte[] apply(byte[] before, byte[] delta) {
        var in = new FriendlyByteBuf(Unpooled.wrappedBuffer(delta));
        try {
            int size = in.readVarInt();
            if (size < 0 || size > MAX_STATE_BYTES) throw new IllegalArgumentException("Invalid UI state length");
            byte[] result = new byte[size];
            int cursor = 0;
            while (cursor < size) {
                boolean copy = in.readBoolean();
                int length = in.readVarInt();
                if (length < 1 || length > size - cursor) throw new IllegalArgumentException("Invalid delta length");
                if (copy) {
                    int offset = in.readVarInt();
                    if (offset < 0 || offset > before.length - length)
                        throw new IllegalArgumentException("Invalid delta copy");
                    System.arraycopy(before, offset, result, cursor, length);
                } else in.readBytes(result, cursor, length);
                cursor += length;
            }
            if (in.isReadable()) throw new IllegalArgumentException("Trailing UI delta bytes");
            return result;
        } finally {
            in.release();
        }
    }

    private static void writeLiteral(FriendlyByteBuf out, byte[] bytes, int start, int end) {
        out.writeBoolean(false);
        out.writeVarInt(end - start);
        out.writeBytes(bytes, start, end - start);
    }

    private static int hash(byte[] bytes, int start) {
        int value = 1;
        for (int i = start; i < start + BLOCK; i++) value = 31 * value + bytes[i];
        return value;
    }

    private StateDelta() {}
}
