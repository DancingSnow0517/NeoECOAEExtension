package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ICraftingInventory;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ECOCraftingInputCachingTest {
    @Test
    void previewSharesPatternMetadataButUsesCurrentInventory() {
        AEKey inputKey = new TestKey();
        AEKey otherKey = new TestKey();
        AtomicInteger inputReads = new AtomicInteger();
        AtomicInteger remainderReads = new AtomicInteger();
        var input = input(inputKey, new AtomicReference<>(inputKey), remainderReads);
        var pattern = pattern(input, inputReads);
        var first = new ECOCraftingInputPreview(inventory(List.of(inputKey)), pattern);
        var second = new ECOCraftingInputPreview(inventory(List.of(otherKey)), pattern);

        assertEquals(List.of(inputKey), keys(first.findFuzzyTemplates(inputKey)));
        assertEquals(List.of(otherKey), keys(second.findFuzzyTemplates(inputKey)));
        assertEquals(1, inputReads.get());
        assertEquals(1, remainderReads.get());

        AE2PatternIntrospection.onRecipeReloadOrServerReload();
        new ECOCraftingInputPreview(inventory(List.of(inputKey)), pattern);
        assertEquals(2, inputReads.get());
        assertEquals(2, remainderReads.get());
    }

    @Test
    void nullRemainderIsCachedAndInvalidatedOnReload() {
        AEKey key = new TestKey();
        AEKey newRemainder = new TestKey();
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<AEKey> remainder = new AtomicReference<>();
        var input = input(key, remainder, reads);
        var cache = new ECOCraftingRemainderCache();

        assertNull(cache.get(input, key));
        remainder.set(newRemainder);
        assertNull(cache.get(input, key));
        assertEquals(1, reads.get());

        AE2PatternIntrospection.onRecipeReloadOrServerReload();
        assertSame(newRemainder, cache.get(input, key));
        assertEquals(2, reads.get());
    }

    private static IPatternDetails.IInput input(AEKey key, AtomicReference<AEKey> remainder,
            AtomicInteger remainderReads) {
        return new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[] {new GenericStack(key, 1)};
            }

            @Override
            public long getMultiplier() {
                return 1;
            }

            @Override
            public boolean isValid(AEKey candidate, net.minecraft.world.level.Level level) {
                return key.equals(candidate);
            }

            @Override
            public AEKey getRemainingKey(AEKey template) {
                remainderReads.incrementAndGet();
                return remainder.get();
            }
        };
    }

    private static IPatternDetails pattern(IPatternDetails.IInput input, AtomicInteger inputReads) {
        return (IPatternDetails) Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(), new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInputs" -> {
                        inputReads.incrementAndGet();
                        yield new IPatternDetails.IInput[] {input};
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static ICraftingInventory inventory(List<AEKey> keys) {
        return new ICraftingInventory() {
            @Override
            public void insert(AEKey key, long amount, Actionable mode) {}

            @Override
            public long extract(AEKey key, long amount, Actionable mode) {
                return keys.contains(key) ? amount : 0;
            }

            @Override
            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                return keys;
            }
        };
    }

    private static List<AEKey> keys(Iterable<AEKey> source) {
        List<AEKey> result = new ArrayList<>();
        source.forEach(result::add);
        return result;
    }

    private static final class TestKey extends AEKey {
        @Override public appeng.api.stacks.AEKeyType getType() { return null; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public net.minecraft.nbt.CompoundTag toTag() { return new net.minecraft.nbt.CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public net.minecraft.resources.ResourceLocation getId() { return null; }
        @Override public void writeToPacket(net.minecraft.network.FriendlyByteBuf buf) {}
        @Override protected net.minecraft.network.chat.Component computeDisplayName() {
            return net.minecraft.network.chat.Component.literal("test");
        }
        @Override public void addDrops(long amount, List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {}
    }
}
