package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.level.Level;

/** Exercises the production CPU with AE2's real input extraction and inventory. */
class ECOSimpleCraftingCPUTest {
    private static final AEKey PLANK = new ECOCraftingTestKey("plank");
    private static final AEKey TABLE = new ECOCraftingTestKey("table");
    private static final AEKey SEED = new ECOCraftingTestKey("seed");
    private static final AEKey CRYSTAL = new ECOCraftingTestKey("crystal");
    private static final AEKey ESSENCE = new ECOCraftingTestKey("essence");
    private static Object previousTypes, previousRegistry;

    @BeforeAll static void initializeKeyRegistry() throws Exception {
        var types = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        var registry = AEKeyTypesInternal.class.getDeclaredField("registry");
        types.setAccessible(true);
        registry.setAccessible(true);
        previousTypes = types.get(null);
        previousRegistry = registry.get(null);
        types.set(null, Set.of(PLANK.getType()));
        registry.set(null, proxy(net.minecraft.core.Registry.class, (method, args) ->
            method.equals("byNameCodec")
                ? com.mojang.serialization.Codec.STRING.xmap(id -> PLANK.getType(), type -> type.getId().toString())
                : null));
    }

    @AfterAll static void restoreKeyRegistry() throws Exception {
        var types = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        var registry = AEKeyTypesInternal.class.getDeclaredField("registry");
        types.setAccessible(true);
        registry.setAccessible(true);
        types.set(null, previousTypes);
        registry.set(null, previousRegistry);
    }

    @Test void restoreSavedInventoryWaitingLinkAndRemainingOutput() throws Exception {
        var f = new Fixture();
        f.start(TABLE, 2, Map.of());
        // AE2's encoder initializes mod items, which requires NeoForge's server bootstrap.
        // Use an explicit saved NBT fixture to exercise the real CPU/Job decoder in the unit JVM.
        var data = new net.minecraft.nbt.CompoundTag();
        var registries = net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty());
        var inventory = new net.minecraft.nbt.ListTag();
        inventory.add(savedStack("table", 1));
        data.put("inventory", inventory);
        var job = new net.minecraft.nbt.CompoundTag();
        var link = new net.minecraft.nbt.CompoundTag();
        f.logic.getJob().link.writeToNBT(link);
        job.put("link", link);
        job.put("finalOutput", savedStack("table", 2));
        job.putLong("remainingAmount", 2);
        var waiting = new net.minecraft.nbt.ListTag();
        waiting.add(savedStack("table", 1));
        job.put("waitingFor", waiting);
        data.put("job", job);
        var restored = new Fixture();
        restored.logic.readFromNBT(data, registries);
        assertEquals(f.logic.getLastLink().getCraftingID(), restored.logic.getLastLink().getCraftingID());
        assertEquals(1, restored.logic.getStored(TABLE));
        assertEquals(1, restored.logic.getWaitingFor(TABLE));
        assertEquals(2, restored.logic.getRemainingJobOutputAmount());
        restored.tick();
        assertEquals(1, restored.logic.getRemainingJobOutputAmount());
        restored.logic.insert(TABLE, 1, Actionable.MODULATE);
        restored.tick();
        assertFalse(restored.logic.hasJob());
        assertEquals(2, restored.delivered.get(TABLE));
        restored.logic.writeToNBT(data, registries);
        assertFalse(data.contains("job"));
    }

    private static net.minecraft.nbt.CompoundTag savedStack(String variant, long amount) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("#t", PLANK.getType().getId().toString());
        tag.putString("variant", variant);
        tag.putLong("#", amount);
        return tag;
    }

    @Test void planksBecomeCraftingTable() throws Exception {
        var f = new Fixture();
        var pattern = new Pattern(PLANK, 4, TABLE, 1);
        f.start(TABLE, 1, Map.of(pattern, 1L));
        f.logic.getInventory().insert(PLANK, 4, Actionable.MODULATE);
        assertEquals(1, f.push());
        assertEquals(0, f.logic.getStored(PLANK));
        assertEquals(1, f.logic.getWaitingFor(TABLE));
        assertEquals(0, f.logic.getStored(TABLE));
        assertEquals(1, f.logic.insert(TABLE, 1, Actionable.MODULATE));
        assertEquals(1, f.logic.getStored(TABLE));
        f.tick();
        assertFalse(f.logic.hasJob());
        assertEquals(1, f.delivered.get(TABLE));
    }

    @Test void halfMillionEssenceWaitsForCrystalRecipe() throws Exception {
        var f = new Fixture();
        var crystal = new Pattern(SEED, 1, CRYSTAL, 1);
        var essence = new Pattern(CRYSTAL, 1, ESSENCE, 1);
        f.start(ESSENCE, 500_000, Map.of(crystal, 500_000L, essence, 500_000L));
        f.logic.getInventory().insert(SEED, 500_000, Actionable.MODULATE);
        assertEquals(0, f.logic.getStored(CRYSTAL));
        assertEquals(1, f.push());
        assertSame(crystal, f.provider.last);
        assertEquals(500_000, f.logic.getJob().tasks.get(essence).value);
        // Block the upstream provider while the crystal is in flight.
        f.provider.busy = true;
        assertEquals(0, f.push());
        assertEquals(1, f.logic.insert(CRYSTAL, 1, Actionable.MODULATE));
        f.provider.busy = false;
        // Exhaust the available seed so only the dependent recipe is physically runnable.
        f.logic.getInventory().extract(SEED, Long.MAX_VALUE, Actionable.MODULATE);
        assertEquals(1, f.push());
        assertSame(essence, f.provider.last);
        assertEquals(499_999, f.logic.getJob().tasks.get(essence).value);
        assertEquals(0, f.logic.getStored(CRYSTAL));
        f.logic.insert(ESSENCE, 1, Actionable.MODULATE);
        f.logic.getInventory().insert(SEED, 499_999, Actionable.MODULATE);
        for (int i = 0; i < 999_998; i++) {
            assertEquals(1, f.push());
            for (var output : f.provider.last.getOutputs()) {
                assertEquals(output.amount(), f.logic.insert(output.what(), output.amount(), Actionable.MODULATE));
            }
        }
        f.tick();
        assertFalse(f.logic.hasJob());
        assertEquals(500_000, f.delivered.get(ESSENCE));
    }

    @Test void busyProvidersAreSkippedAndRejectedPushStopsUntilNextTick() throws Exception {
        var f = new Fixture();
        var pattern = new Pattern(PLANK, 4, TABLE, 1);
        f.start(TABLE, 1, Map.of(pattern, 1L));
        f.logic.getInventory().insert(PLANK, 4, Actionable.MODULATE);
        var busy = new Provider();
        busy.busy = true;
        var later = new Provider();
        f.providers = List.of(busy, f.provider, later);
        f.provider.accept = false;
        assertEquals(0, f.push());
        assertEquals(0, busy.calls);
        assertEquals(1, f.provider.calls);
        assertEquals(0, later.calls);
        assertEquals(4, f.logic.getStored(PLANK));
        assertEquals(0, f.logic.getWaitingFor(TABLE));
        assertEquals(1, f.logic.getJob().tasks.get(pattern).value);
        f.provider.accept = true;
        assertEquals(1, f.push());
        assertEquals(0, f.logic.getStored(PLANK));
    }

    @Test void allProvidersBusyCanResume() throws Exception {
        var f = new Fixture();
        f.start(TABLE, 1, Map.of(new Pattern(PLANK, 4, TABLE, 1), 1L));
        f.logic.getInventory().insert(PLANK, 4, Actionable.MODULATE);
        f.provider.busy = true;
        for (int i = 0; i < 30; i++) assertEquals(0, f.push());
        assertEquals(4, f.logic.getStored(PLANK));
        f.provider.busy = false;
        assertEquals(1, f.push());
    }

    @Test void failedDeliveryKeepsPhysicalOutputAndRetries() throws Exception {
        var f = new Fixture();
        f.start(TABLE, 1, Map.of());
        f.logic.getJob().waitingFor.insert(TABLE, 1, Actionable.MODULATE);
        assertEquals(1, f.logic.insert(TABLE, 1, Actionable.SIMULATE));
        assertEquals(0, f.logic.getStored(TABLE));
        f.logic.insert(TABLE, 1, Actionable.MODULATE);
        f.rejectDelivery = true;
        f.tick();
        assertTrue(f.logic.hasJob());
        assertEquals(1, f.logic.getStored(TABLE));
        assertEquals(1, f.logic.getRemainingJobOutputAmount());
        f.rejectDelivery = false;
        f.throwDelivery = true;
        f.tick();
        assertEquals(1, f.logic.getStored(TABLE));
        f.throwDelivery = false;
        f.tick();
        assertFalse(f.logic.hasJob());
        assertEquals(1, f.delivered.get(TABLE));
    }

    @Test void completionAlsoWaitsForContainerOutputs() throws Exception {
        var f = new Fixture();
        f.start(TABLE, 1, Map.of());
        f.logic.getJob().waitingFor.insert(TABLE, 1, Actionable.MODULATE);
        f.logic.getJob().waitingFor.insert(SEED, 1, Actionable.MODULATE);
        f.logic.insert(TABLE, 1, Actionable.MODULATE);
        f.tick();
        assertEquals(0, f.logic.getRemainingJobOutputAmount());
        assertTrue(f.logic.hasJob());
        f.logic.insert(SEED, 1, Actionable.MODULATE);
        f.tick();
        assertFalse(f.logic.hasJob());
        assertEquals(1, f.delivered.get(SEED));
    }

    private record Pattern(AEKey input, long inputAmount, AEKey output, long outputAmount)
            implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, outputAmount)); }
        @Override public IInput[] getInputs() {
            return new IInput[] { new IInput() {
                public GenericStack[] getPossibleInputs() { return new GenericStack[] { new GenericStack(input, inputAmount) }; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level level) { return input.equals(key); }
                public AEKey getRemainingKey(AEKey key) { return null; }
            }};
        }
    }

    private static class Provider implements ICraftingProvider {
        boolean busy, accept = true;
        int calls;
        IPatternDetails last;
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public boolean isBusy() { return busy; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            calls++;
            last = pattern;
            return accept;
        }
    }

    private static class Fixture {
        final Provider provider = new Provider();
        List<ICraftingProvider> providers = List.of(provider);
        final KeyCounter delivered = new KeyCounter();
        boolean rejectDelivery, throwDelivery;
        final MEStorage storage = proxy(MEStorage.class, (method, args) -> {
            if (method.equals("insert")) {
                if (throwDelivery) throw new IllegalStateException("test delivery failure");
                if (rejectDelivery) return 0L;
                if (args[2] == Actionable.MODULATE) delivered.add((AEKey) args[0], (long) args[1]);
                return args[1];
            }
            return null;
        });
        final IStorageService storageService = proxy(IStorageService.class,
            (method, args) -> method.equals("getInventory") ? storage : null);
        final IEnergyService energy = proxy(IEnergyService.class,
            (method, args) -> method.equals("extractAEPower") ? args[0] : null);
        final CraftingService crafting = new CraftingService(null, storageService, energy) {
            @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) { return providers; }
        };
        final IGrid grid = proxy(IGrid.class, (method, args) -> switch (method) {
            case "getStorageService" -> storageService;
            case "getCraftingService" -> crafting;
            default -> null;
        });
        final ECOCraftingCPU cpu = new ECOCraftingCPU(null, (IECOTier) null) {
            @Override public void markDirty() {}
            @Override public boolean isActive() { return true; }
            @Override public IGrid getGrid() { return grid; }
            @Override public Level getLevel() { return null; }
            @Override public IActionSource getActionSource() { return null; }
        };
        final ECOCraftingCPULogic logic = cpu.getLogic();

        void start(AEKey output, long amount, Map<IPatternDetails, Long> tasks) throws Exception {
            ICraftingPlan plan = proxy(ICraftingPlan.class, (method, args) -> switch (method) {
                case "finalOutput" -> new GenericStack(output, amount);
                case "patternTimes" -> tasks;
                case "emittedItems", "usedItems", "missingItems" -> new KeyCounter();
                default -> null;
            });
            var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
            var job = new ExecutingCraftingJob(plan, key -> {}, link, null);
            var field = ECOCraftingCPULogic.class.getDeclaredField("job");
            field.setAccessible(true);
            field.set(logic, job);
        }
        int push() { return logic.executeCrafting(1, crafting, energy, null); }
        void tick() { logic.tickCraftingLogic(energy, crafting); }
    }

    @FunctionalInterface private interface Handler { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, args) -> handler.call(method.getName(), args));
    }
}
