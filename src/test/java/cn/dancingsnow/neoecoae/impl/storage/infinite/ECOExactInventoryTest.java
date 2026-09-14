package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.mixins.ae2.NetworkStorageAccessor;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
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
                ECOExactInventory.hugeAmounts(new Network(List.of(first, alias, ordinary)))
                        .get(key));
    }

    @Test
    void exactOverlayDisappearsAtLongBoundaryAndAfterUnmount() {
        AEKey key = new TestKey();
        var finite = new ECOInfiniteStorage(engine(key, BigInteger.valueOf(Long.MAX_VALUE)), Component.empty());
        assertTrue(ECOExactInventory.hugeAmounts(finite).isEmpty());
        assertTrue(ECOExactInventory.hugeAmounts(new Network(List.of())).isEmpty());
    }

    @Test
    void ordinaryNetworksAreNotRescannedOrOverridden() {
        MEStorage ordinary = new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter out) {
                fail("No ECO mount: do not scan contents");
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
        assertTrue(ECOExactInventory.hugeAmounts(new Network(List.of(ordinary))).isEmpty());
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

    private record Network(List<MEStorage> children) implements MEStorage, NetworkStorageAccessor {
        @Override
        public NavigableMap<Integer, List<MEStorage>> neoecoae$getMountedInventories() {
            var result = new TreeMap<Integer, List<MEStorage>>();
            result.put(0, children);
            return result;
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
