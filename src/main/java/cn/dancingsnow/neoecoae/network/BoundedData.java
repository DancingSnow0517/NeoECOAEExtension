package cn.dancingsnow.neoecoae.network;

import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;

/** Shared allocation and wire budgets. Limits apply before a packet reaches Forge/AE2. */
public final class BoundedData {
    public static final int PART_BYTES = 16 * 1024;
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    public static byte[] encode(int limit, Consumer<FriendlyByteBuf> writer) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(Math.min(256, limit), limit));
        try {
            writer.accept(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    /** Ordered reliable transport; publish only once all parts have arrived. */
    public static final class Receiver {
        private byte[] bytes;
        private int offset;

        public byte[] accept(int total, int start, byte[] part) {
            if (total < 1
                    || total > MAX_BYTES
                    || start < 0
                    || part.length < 1
                    || part.length > PART_BYTES
                    || start > total - part.length) {
                clear();
                throw new IllegalArgumentException("Invalid sync fragment");
            }
            if (start == 0) {
                bytes = new byte[total];
                offset = 0;
            }
            if (bytes == null || bytes.length != total || offset != start) {
                clear();
                throw new IllegalArgumentException("Out of sequence sync fragment");
            }
            System.arraycopy(part, 0, bytes, offset, part.length);
            offset += part.length;
            if (offset != total) return null;
            byte[] completed = bytes;
            clear();
            return completed;
        }

        public void clear() {
            bytes = null;
            offset = 0;
        }
    }

    private BoundedData() {}
}
