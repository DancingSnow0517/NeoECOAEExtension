package cn.dancingsnow.neoecoae.platform.network;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Small project-owned encoder/decoder pair used by Forge SimpleChannel and our menu transport. */
public record NEStreamCodec<B, V>(BiConsumer<B, V> encoder, Function<B, V> decoder) {
    public NEStreamCodec {
        Objects.requireNonNull(encoder);
        Objects.requireNonNull(decoder);
    }

    public static <B, V> NEStreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new NEStreamCodec<>(encoder, decoder);
    }

    public void encode(B buffer, V value) { encoder.accept(buffer, value); }
    public V decode(B buffer) { return decoder.apply(buffer); }

    public <U> NEStreamCodec<B, U> map(Function<V, U> read, Function<U, V> write) {
        return of((buffer, value) -> encode(buffer, write.apply(value)), buffer -> read.apply(decode(buffer)));
    }
}
