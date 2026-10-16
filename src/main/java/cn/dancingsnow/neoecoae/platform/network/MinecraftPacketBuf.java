package cn.dancingsnow.neoecoae.platform.network;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.network.PacketBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Minecraft 1.21.1 implementation; preserves FriendlyByteBuf's existing VarInt and byte-array format. */
public final class MinecraftPacketBuf implements PacketBuf<AEKey> {
    private final FriendlyByteBuf delegate;

    public MinecraftPacketBuf(FriendlyByteBuf delegate) { this.delegate = delegate; }
    @Override public void writeVarInt(int value) { delegate.writeVarInt(value); }
    @Override public int readVarInt() { return delegate.readVarInt(); }
    @Override public void writeVarLong(long value) { delegate.writeVarLong(value); }
    @Override public long readVarLong() { return delegate.readVarLong(); }
    @Override public void writeUtf(String value) { delegate.writeUtf(value); }
    @Override public String readUtf() { return delegate.readUtf(); }
    @Override public void writeBoolean(boolean value) { delegate.writeBoolean(value); }
    @Override public boolean readBoolean() { return delegate.readBoolean(); }
    @Override public void writeBytes(byte[] value) { delegate.writeByteArray(value); }
    @Override public byte[] readBytes(int maximumLength) { return delegate.readByteArray(maximumLength); }
    @Override public void writeKey(AEKey key) { AEKey.writeKey(registryBuffer(), key); }
    @Override public AEKey readKey() { return AEKey.readKey(registryBuffer()); }

    private RegistryFriendlyByteBuf registryBuffer() {
        if (delegate instanceof RegistryFriendlyByteBuf registries) return registries;
        throw new IllegalStateException("Key packets require registry access");
    }
}
