import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter;
import cn.dancingsnow.neoecoae.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.mixins.ae2.storage.NetworkStorageMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Offline conservation checks. Uses the pinned AE2 network, creative cell and production mount hooks. */
public final class StorageInterfaceTransferCheck {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final AEKey KEY = new TestKey();
    private static final NetworkStorageMixin HOOKS = new NetworkStorageMixin() {};
    private static final Method INSERT = hook("neoecoae$excludeCreativeOutput");
    private static final Method EXTRACT = hook("neoecoae$filterCreativeInput");

    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String name : args.length == 0 ? List.of("cached-input", "mixed-input", "creative-output", "full-target",
                "nested-network", "finite-256k-capacity", "host-creative-sink") : List.of(args)) {
            try {
                switch (name) {
                    case "cached-input" -> cachedInput();
                    case "mixed-input" -> mixedInput();
                    case "creative-output" -> output(false);
                    case "full-target" -> output(true);
                    case "nested-network" -> nestedNetwork();
                    case "finite-256k-capacity" -> finiteCapacity();
                    case "host-creative-sink" -> hostCreativeSink();
                    default -> throw new AssertionError(name);
                }
                System.out.println("PASS " + name);
            } catch (AssertionError failure) {
                failures++;
                System.out.println("FAIL " + name + ": " + failure.getMessage());
            }
        }
        if (failures != 0) throw new AssertionError(failures + " conservation check(s) failed");
    }

    private static void cachedInput() {
        Finite cell = new Finite(128, 256);
        long extracted = ECOCreativeExtractionFilter.extract(cell, KEY, 64, SOURCE);
        check(cell.stock == 64 && cell.listing.get(KEY) == 128, "source debited while its tick cache stays unchanged");
        check(extracted == 64, "a real finite debit must reach the destination; received " + extracted);
    }

    private static void mixedInput() throws Exception {
        Finite cell = new Finite(73, 256);
        NetworkStorage network = network(0, cell);
        network.mount(-1, new Mounted(creative()));
        Finite host = new Finite(0, 200);
        long moved = move(network, host, true, true);
        check(cell.stock == 0 && host.stock == 73 && moved == 73,
            "ignore the creative source before extraction and retain the finite source's 73 items");
    }

    private static void output(boolean full) throws Exception {
        Finite cell = new Finite(full ? 96 : 0, 96);
        NetworkStorage network = network(0, cell);
        network.mount(100, new Mounted(creative()));
        Finite host = new Finite(128, 256);
        long before = host.stock + cell.stock;
        long moved = move(network, host, false, false);
        check(host.stock + cell.stock == before, "creative target consumed physical stock");
        check(moved == (full ? 0 : 96) && host.stock == (full ? 128 : 32),
            "move only what the finite target accepts and retain the remainder");
        check(network.insert(KEY, 1, Actionable.MODULATE, SOURCE) == 1,
            "ordinary AE2 insertion regains its original creative-sink behavior after the IO scope");
    }

    private static void nestedNetwork() throws Exception {
        Finite cell = new Finite(0, 96);
        NetworkStorage inner = network(0, cell);
        inner.mount(100, new Mounted(creative()));
        NetworkStorage outer = network(0, inner);
        Finite host = new Finite(128, 256);
        long moved = move(outer, host, false, false);
        check(moved == 96 && host.stock == 32 && cell.stock == 96,
            "nested network routing must also exclude creative sinks");
    }

    private static void finiteCapacity() {
        Finite cell = new Finite(0, 262144L * 8);
        Finite host = new Finite(3_000_000, 4_000_000);
        long moved = move(network(0, cell), host, false, false);
        check(moved == cell.capacity && host.stock + cell.stock == 3_000_000,
            "a 256K destination keeps overflow in the ECO source");
    }

    private static void hostCreativeSink() throws Exception {
        Finite cell = new Finite(80, 96);
        Finite host = new Finite(0, 96);
        Class<?> type = Class.forName(
            "cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageInterfaceTransfer$CombinedStorage");
        var constructor = type.getDeclaredConstructor(List.class, Component.class);
        constructor.setAccessible(true);
        MEStorage combined = (MEStorage) constructor.newInstance(List.of(creative(), host), Component.literal("host"));
        long moved = move(network(0, cell), combined, true, false);
        check(moved == 80 && cell.stock == 0 && host.stock == 80,
            "input must bypass creative cells inside the ECO host as well");
    }

    private static long move(NetworkStorage network, MEStorage host, boolean input, boolean ignoreCreative) {
        IStorageService storage = proxy(IStorageService.class, (method, args) -> switch (method) {
            case "getInventory" -> network;
            case "getCachedInventory" -> network.getAvailableStacks();
            default -> throw new AssertionError(method);
        });
        IEnergyService energy = proxy(IEnergyService.class, (method, args) -> {
            if (method.equals("extractAEPower")) return args[0];
            throw new AssertionError(method);
        });
        IGrid grid = proxy(IGrid.class, (method, args) -> switch (method) {
            case "getStorageService" -> storage;
            case "getEnergyService" -> energy;
            default -> throw new AssertionError(method);
        });
        return new ECOIOPortTransfer().transfer(grid, host, input, ignoreCreative, false, SOURCE, key -> true);
    }

    private static NetworkStorage network(int priority, MEStorage storage) {
        NetworkStorage network = new NetworkStorage();
        network.mount(priority, new Mounted(storage));
        return network;
    }

    private static CreativeView creative() throws Exception {
        // Execute the pinned AE2's actual creative insert/extract methods without booting a world.
        Class<?> type = Class.forName("appeng.me.cells.CreativeCellInventory");
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MEStorage cell = (MEStorage) unsafe.allocateInstance(type);
        field(cell, type, "configured", Set.of(KEY));
        return new CreativeView(cell);
    }

    /** The production marker is attached to AE2's package-private class by CreativeCellInventoryMixin. */
    private static final class CreativeView extends DelegatingMEInventory
            implements ECOCreativeExtractionFilter.CreativeStorage {
        CreativeView(MEStorage delegate) { super(delegate); }
    }

    /** Adapts the actual private mixin operations to an ordinary JVM without a Forge/Mixin launcher. */
    private static final class Mounted extends DelegatingMEInventory
            implements cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor {
        Mounted(MEStorage delegate) { super(delegate); }
        @Override public MEStorage neoecoae$getDelegate() { return getDelegate(); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            Operation<Long> original = args -> getDelegate().insert(
                (AEKey) args[1], (long) args[2], (Actionable) args[3], (IActionSource) args[4]);
            return invoke(INSERT, getDelegate(), key, amount, mode, source, original);
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            Operation<Long> original = args -> getDelegate().extract(
                (AEKey) args[1], (long) args[2], (Actionable) args[3], (IActionSource) args[4]);
            return invoke(EXTRACT, getDelegate(), key, amount, mode, source, original);
        }
    }

    private static final class Finite implements MEStorage {
        long stock;
        final long capacity;
        final KeyCounter listing = new KeyCounter();
        Finite(long stock, long capacity) { this.stock = stock; this.capacity = capacity; listing.set(KEY, stock); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, capacity - stock);
            if (mode == Actionable.MODULATE) stock += accepted;
            return accepted;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, stock);
            if (mode == Actionable.MODULATE) stock -= extracted;
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) { out.addAll(listing); }
        @Override public Component getDescription() { return Component.literal("finite cell"); }
    }

    private static final class TestType extends AEKeyType {
        TestType() { super(new ResourceLocation("check", "transfer"), TestKey.class, Component.literal("transfer")); }
        @Override public AEKey readFromPacket(FriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { throw new UnsupportedOperationException(); }
    }

    private static final class TestKey extends AEKey {
        private static final TestType TYPE = new TestType();
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return this; }
        @Override public ResourceLocation getId() { return TYPE.getId(); }
        @Override public void writeToPacket(FriendlyByteBuf buffer) {}
        @Override protected Component computeDisplayName() { return Component.literal("transfer"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    private interface Call { Object run(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (self, method, args) -> call.run(method.getName(), args)));
    }
    private static Method hook(String name) {
        try {
            Method method = NetworkStorageMixin.class.getDeclaredMethod(name, MEStorage.class, AEKey.class,
                long.class, Actionable.class, IActionSource.class, Operation.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static long invoke(Method method, Object... args) {
        try { return (long) method.invoke(HOOKS, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void field(Object instance, Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
    private static void check(boolean result, String reason) { if (!result) throw new AssertionError(reason); }
}
