package cn.dancingsnow.neoecoae.network;

/** Ordered, bounded reassembly. A new transfer supersedes an incomplete old transfer. */
public final class MenuStreamAssembler {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private long transfer = -1;
    private byte[] bytes;
    private int offset;

    public byte[] accept(long id, int total, int position, byte[] part) {
        if (total < 1 || total > MAX_BYTES || position < 0 || part.length == 0
                || part.length > total || position > total - part.length) {
            throw new IllegalArgumentException("Invalid menu stream bounds");
        }
        if (position == 0) {
            transfer = id;
            bytes = new byte[total];
            offset = 0;
        }
        if (bytes == null || transfer != id || bytes.length != total || position != offset) {
            throw new IllegalArgumentException("Out-of-order menu stream");
        }
        System.arraycopy(part, 0, bytes, offset, part.length);
        offset += part.length;
        if (offset != total) return null;
        byte[] complete = bytes;
        bytes = null;
        return complete;
    }
}
