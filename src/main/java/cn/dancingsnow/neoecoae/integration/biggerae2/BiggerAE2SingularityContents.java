package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.math.BigInteger;
import java.util.List;

/** Immutable stack component: exact quantities survive copies, saves and client synchronization. */
record BiggerAE2SingularityContents(List<Entry> entries) {
    private static final DeferredRegister<DataComponentType<?>> TYPES =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "neoecoae");
    private static final Codec<BigInteger> POSITIVE_AMOUNT = Codec.STRING.comapFlatMap(value -> {
        try {
            BigInteger number = new BigInteger(value);
            return number.signum() > 0 ? DataResult.success(number) : DataResult.error(() -> "Nonpositive cell amount");
        } catch (NumberFormatException invalid) {
            return DataResult.error(() -> "Invalid cell amount");
        }
    }, BigInteger::toString);
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(builder -> builder.group(
        AEKey.CODEC.fieldOf("key").forGetter(Entry::key),
        POSITIVE_AMOUNT.fieldOf("amount").forGetter(Entry::amount)
    ).apply(builder, Entry::new));
    static final Codec<BiggerAE2SingularityContents> CODEC = ENTRY_CODEC.listOf()
        .xmap(BiggerAE2SingularityContents::new, BiggerAE2SingularityContents::entries);
    static final StreamCodec<RegistryFriendlyByteBuf, BiggerAE2SingularityContents> STREAM_CODEC = StreamCodec.of(
        (buffer, contents) -> {
            buffer.writeVarInt(contents.entries.size());
            for (Entry entry : contents.entries) {
                AEKey.STREAM_CODEC.encode(buffer, entry.key());
                buffer.writeUtf(entry.amount().toString());
            }
        },
        buffer -> {
            int count = buffer.readVarInt();
            if (count < 0 || count > 5) throw new IllegalArgumentException("Invalid singularity type count");
            var entries = new java.util.ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(AEKey.STREAM_CODEC.decode(buffer), new BigInteger(buffer.readUtf())));
            }
            return new BiggerAE2SingularityContents(entries);
        });
    static final DataComponentType<BiggerAE2SingularityContents> TYPE = DataComponentType
        .<BiggerAE2SingularityContents>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build();
    static final BiggerAE2SingularityContents EMPTY = new BiggerAE2SingularityContents(List.of());

    static {
        TYPES.register("bigger_ae2_singularity_contents", () -> TYPE);
    }

    BiggerAE2SingularityContents {
        entries = List.copyOf(entries);
        if (entries.size() > 5 || entries.stream().map(Entry::key).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Invalid singularity contents");
        }
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    record Entry(AEKey key, BigInteger amount) {
        Entry {
            java.util.Objects.requireNonNull(key);
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Nonpositive cell amount");
        }
    }
}
