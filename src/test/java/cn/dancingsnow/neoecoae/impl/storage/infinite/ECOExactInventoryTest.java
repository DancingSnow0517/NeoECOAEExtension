package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ECOExactInventoryTest {
    @Test
    void aggregatesMountedCountsWithoutLongOverflowOrDuplicateDomainCounting() {
        AEKey key = new TestKey();
        BigInteger exact = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        var engine = engine(key, exact);
        var first = new ECOInfiniteStorage(engine, Component.literal("one"));
        var alias = new ECOInfiniteStorage(engine, Component.literal("same domain"));
        MEStorage ordinary = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter out) {
                out.add(key, 25);
            }

            @Override
            public Component getDescription() {
                return Component.literal("ordinary");
            }
        };
        assertEquals(
                exact.add(BigInteger.valueOf(25)),
                exactAmounts(new Network(List.of(first, alias, ordinary))).get(key));
    }

    @Test
    void exactOverlayDisappearsAtLongBoundaryAndAfterUnmount() {
        AEKey key = new TestKey();
        var finite = new ECOInfiniteStorage(engine(key, BigInteger.valueOf(Long.MAX_VALUE)), Component.empty());
        assertTrue(exactAmounts(finite).isEmpty());
        assertTrue(exactAmounts(new Network(List.of())).isEmpty());
    }

    @Test
    void ordinaryNetworksAreNotRescannedOrOverridden() {
        int[] scans = {0};
        AEKey key = new TestKey();
        MEStorage ordinary = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter out) {
                scans[0]++;
                out.set(key, Long.MAX_VALUE);
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        assertTrue(exactAmounts(new Network(List.of(ordinary))).isEmpty());
        assertEquals(1, scans[0]);
    }

    @Test
    void nestedNetworksDoNotDoubleCountAndOrdinaryOnlyKeysNeverGetAnOverlay() {
        AEKey ecoKey = new TestKey();
        AEKey ordinaryKey = new TestKey();
        BigInteger amount = BigInteger.TEN.pow(30);
        var eco = new ECOInfiniteStorage(engine(ecoKey, amount), Component.empty());
        MEStorage ordinary = storage(ordinaryKey, Long.MAX_VALUE);
        var nested = new Network(List.of(new Network(List.of(eco, ordinary)), storage(ecoKey, 64), ordinary));
        var listing = ExactAmountCollector.collect(nested, nested::getAvailableStacks);
        assertEquals(Map.of(ecoKey, amount.add(BigInteger.valueOf(64))), listing.amounts());
        assertEquals(Long.MAX_VALUE, listing.stacks().get(ecoKey));
        assertEquals(Long.MAX_VALUE, listing.stacks().get(ordinaryKey));
    }

    @Test
    void inaccessibleEcoDoesNotQualifyAnOrdinaryKeyForExactDisplay() {
        AEKey key = new TestKey();
        var eco = new ECOInfiniteStorage(engine(key, BigInteger.TEN.pow(30)), Component.empty(), () -> false);
        assertTrue(exactAmounts(new Network(List.of(eco, storage(key, Long.MAX_VALUE))))
                .isEmpty());
    }

    @Test
    void inaccessibleAliasDoesNotHideTheAccessibleMount() {
        AEKey key = new TestKey();
        BigInteger amount = BigInteger.TEN.pow(30);
        var engine = engine(key, amount);
        var hidden = new ECOInfiniteStorage(engine, Component.empty(), () -> false);
        var visible = new ECOInfiniteStorage(engine, Component.empty());
        assertEquals(Map.of(key, amount), exactAmounts(new Network(List.of(hidden, visible))));
    }

    @Test
    void smallEcoContributionStillQualifiesAnOverflowingNetworkTotal() {
        AEKey key = new TestKey();
        var eco = new ECOInfiniteStorage(engine(key, BigInteger.TEN), Component.empty());
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN),
                exactAmounts(new Network(List.of(storage(key, Long.MAX_VALUE), eco)))
                        .get(key));
    }

    @Test
    void failedListingDoesNotLeakCollectorState() {
        AEKey key = new TestKey();
        var eco = new ECOInfiniteStorage(engine(key, BigInteger.TEN.pow(30)), Component.empty());
        assertThrows(
                IllegalStateException.class,
                () -> ExactAmountCollector.collect(eco, () -> {
                    throw new IllegalStateException("listing failed");
                }));
        assertTrue(exactAmounts(storage(key, Long.MAX_VALUE)).isEmpty());
        assertEquals(BigInteger.TEN.pow(30), exactAmounts(eco).get(key));
    }

    private static Map<AEKey, BigInteger> exactAmounts(MEStorage storage) {
        return ExactAmountCollector.collect(storage, storage::getAvailableStacks)
                .amounts();
    }

    private static MEStorage storage(AEKey key, long amount) {
        return new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter out) {
                out.set(key, amount);
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
    }

    private static ECOInfiniteStorageEngine engine(AEKey key, BigInteger amount) {
        return (ECOInfiniteStorageEngine) Proxy.newProxyInstance(
                ECOInfiniteStorageEngine.class.getClassLoader(),
                new Class<?>[] {ECOInfiniteStorageEngine.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAmount" -> HugeAmount.of(amount);
                    case "getAvailableStacks" -> {
                        ((KeyCounter) args[0]).set(key, HugeAmount.of(amount).toLongSaturated());
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Network(List<MEStorage> children) implements MEStorage {
        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (MEStorage child : children) {
                KeyCounter contribution = new KeyCounter();
                ExactAmountCollector.contribution(child, contribution, () -> child.getAvailableStacks(contribution));
                SaturatingStackAccumulator.addAll(out, contribution);
            }
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    }

    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public net.minecraft.nbt.CompoundTag toTag() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(net.minecraft.network.FriendlyByteBuf buffer) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(
                long amount,
                List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level,
                net.minecraft.core.BlockPos pos) {}
    }
}
