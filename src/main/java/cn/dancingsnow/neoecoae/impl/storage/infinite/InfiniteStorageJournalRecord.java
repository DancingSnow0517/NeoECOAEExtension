package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Reusable, server-thread-only frame in the existing length-prefixed NBT journal format. */
final class InfiniteStorageJournalRecord {
    private final ByteBuffer frame;
    private final int sequenceOffset;
    private final int amountOffset;
    private final int addedOffset;

    InfiniteStorageJournalRecord(CompoundTag key) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0); // Frame length, filled after encoding.
            out.writeByte(Tag.TAG_COMPOUND);
            out.writeUTF("");
            out.writeByte(Tag.TAG_LONG);
            out.writeUTF("sequence");
            sequenceOffset = bytes.size();
            out.writeLong(0L);
            out.writeByte(Tag.TAG_LONG);
            out.writeUTF("amount");
            amountOffset = bytes.size();
            out.writeLong(0L);
            out.writeByte(Tag.TAG_BYTE);
            out.writeUTF("added");
            addedOffset = bytes.size();
            out.writeByte(0);
            out.writeByte(Tag.TAG_COMPOUND);
            out.writeUTF("key");
            key.write(out);
            out.writeByte(Tag.TAG_END);
        }
        frame = ByteBuffer.wrap(bytes.toByteArray());
        frame.putInt(0, frame.capacity() - Integer.BYTES);
    }

    int size() {
        return frame.capacity();
    }

    ByteBuffer prepare(long sequence, long amount, boolean added) {
        frame.clear();
        frame.putLong(sequenceOffset, sequence);
        frame.putLong(amountOffset, amount);
        frame.put(addedOffset, (byte) (added ? 1 : 0));
        return frame;
    }
}
