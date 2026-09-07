package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOCraftingDispatchRegressionTest {
    private static final AEKey INPUT = new StorageTestKey("dispatch_input");
    private static final AEKey OUTPUT = new StorageTestKey("dispatch_output");
    private static Object previousTypes;
    private static Level level;

    @org.junit.jupiter.api.BeforeAll static void initialize() throws Exception {
        var field = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        field.setAccessible(true);
        previousTypes = field.get(null);
        field.set(null, java.util.Set.of(INPUT.getType()));
        // This dispatch fixture never calls world methods; only the tick's non-null level guard needs a world.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC,
            "cn/dancingsnow/neoecoae/api/me/DispatchTestLevel", null, "net/minecraft/world/level/Level", null);
        writer.visitEnd();
        var levelClass = java.lang.invoke.MethodHandles.lookup().defineHiddenClass(writer.toByteArray(), false)
            .lookupClass();
        level = (Level) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(levelClass);
    }

    @org.junit.jupiter.api.AfterAll static void restoreTypes() throws Exception {
        var field = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        field.setAccessible(true);
        field.set(null, previousTypes);
    }

    @Test void samePatternFillsAll88LanesInOneTickBeyondTheConfiguredCeiling() throws Exception {
        var f = new Fixture(88L * 400_000);
        var lanes = new ArrayList<Lane>();
        for (int i = 0; i < 88; i++) lanes.add(new Lane());
        f.providers = new ArrayList<>(lanes);
        f.tick(1);
        assertTrue(lanes.stream().allMatch(lane -> lane.accepted == 400_000));
        assertEquals(0, f.logic.getStored(INPUT));
        assertEquals(88L * 400_000, f.logic.getWaitingFor(OUTPUT));
    }

    @Test void fastPathStillRunsAfterOrdinaryBudgetIsExhausted() throws Exception {
        var f = new Fixture(400_001);
        var lane = new Lane();
        f.providers = List.of(f.ordinary(false), lane);
        f.tick(1);
        assertEquals(1, f.ordinaryAttempts);
        assertEquals(400_000, lane.accepted);
        assertEquals(400_001, f.logic.getWaitingFor(OUTPUT));
    }

    @Test void fastPathDoesNotConsumeOrdinaryBudget() throws Exception {
        var f = new Fixture(400_010);
        var lane = new Lane();
        f.providers = List.of(lane, f.ordinary(false));
        f.tick(3);
        assertEquals(400_000, lane.accepted);
        assertEquals(3, f.ordinaryAttempts);
        assertEquals(7, f.logic.getStored(INPUT));
    }

    @Test void zeroOrdinaryBudgetAllowsBatchesButNeverOrdinaryPushes() throws Exception {
        var f = new Fixture(400_010);
        var lane = new Lane();
        f.providers = List.of(f.ordinary(false), lane);
        f.tick(0);
        assertEquals(400_000, lane.accepted);
        assertEquals(0, f.ordinaryAttempts);
        assertEquals(10, f.logic.getStored(INPUT));
    }

    @Test void rejectedOrdinaryPushRestoresInputAndTerminatesThePass() throws Exception {
        var f = new Fixture(10);
        f.providers = List.of(f.ordinary(true));
        f.tick(3);
        assertEquals(1, f.ordinaryAttempts);
        assertEquals(10, f.logic.getStored(INPUT));
        assertEquals(0, f.logic.getWaitingFor(OUTPUT));
    }

    @Test void busyFirstPatternDoesNotStarveAnotherReadyPattern() throws Exception {
        var f = new Fixture(400_000);
        var other = new IPatternDetails() {
            public AEItemKey getDefinition() { return null; }
            public List<GenericStack> getOutputs() { return f.pattern.getOutputs(); }
            public IInput[] getInputs() { return f.pattern.getInputs(); }
        };
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.value = 400_000;
        f.logic.getJob().tasks.put(other, progress);
        f.blockedPattern = f.logic.getJob().tasks.keySet().iterator().next();
        var lane = new Lane();
        f.providers = List.of(lane);
        f.tick(1);
        assertEquals(400_000, lane.accepted);
        assertEquals(400_000, f.logic.getJob().tasks.get(f.blockedPattern).value);
        assertEquals(400_000, f.logic.getWaitingFor(OUTPUT));
    }

    private static class Lane implements ICraftingProvider, ECOBatchCapacityProvider {
        long accepted;
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public boolean isBusy() { return accepted > 0; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            throw new AssertionError("Expected atomic batch dispatch");
        }
        public long eco$getBatchCapacity(ECOBatchDispatchContext context) { return isBusy() ? 0 : 400_000; }
        public boolean eco$pushBatch(ECOBatchDispatchContext context, long count) {
            assertTrue(count > 0 && count <= eco$getBatchCapacity(context));
            accepted = count;
            return true;
        }
    }

    private static class Fixture {
        List<ICraftingProvider> providers = List.of();
        IPatternDetails blockedPattern;
        int ordinaryAttempts;
        final IPatternDetails pattern = new IPatternDetails() {
            public AEItemKey getDefinition() { return null; }
            public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
            public IInput[] getInputs() { return new IInput[] { new IInput() {
                public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(INPUT, 1)}; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level level) { return INPUT.equals(key); }
                public AEKey getRemainingKey(AEKey key) { return null; }
            }}; }
        };
        final IEnergyService energy = proxy(IEnergyService.class, (method, args) ->
            method.equals("extractAEPower") ? args[0] : null);
        final CraftingService crafting = new CraftingService(null,
                proxy(IStorageService.class, (method, args) -> null), energy) {
            @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) {
                return pattern == blockedPattern ? List.of() : providers;
            }
        };
        final ECOCraftingCPU cpu = new ECOCraftingCPU(null, (IECOTier) null) {
            @Override public void markDirty() {}
            @Override public IGrid getGrid() { return null; }
            @Override public boolean isActive() { return true; }
            @Override public Level getLevel() { return level; }
            @Override public int getCoProcessors() { return Integer.MAX_VALUE; }
        };
        final ECOCraftingCPULogic logic = cpu.getLogic();

        Fixture(long crafts) throws Exception {
            var plan = proxy(ICraftingPlan.class, (method, args) -> switch (method) {
                case "finalOutput" -> new GenericStack(OUTPUT, crafts);
                case "patternTimes" -> Map.of(pattern, crafts);
                case "emittedItems", "usedItems", "missingItems" -> new KeyCounter();
                default -> null;
            });
            var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
            var field = ECOCraftingCPULogic.class.getDeclaredField("job");
            field.setAccessible(true);
            field.set(logic, new ExecutingCraftingJob(plan, key -> {}, link, null));
            logic.getInventory().insert(INPUT, crafts, Actionable.MODULATE);
        }

        ICraftingProvider ordinary(boolean reject) {
            return new ICraftingProvider() {
                public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
                public boolean isBusy() { return false; }
                public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                    ordinaryAttempts++;
                    return !reject;
                }
            };
        }

        void tick(int limit) {
            int previous = NEConfig.ecoCpuPushTickLimit;
            try {
                NEConfig.ecoCpuPushTickLimit = limit;
                logic.tickCraftingLogic(energy, crafting);
            } finally {
                NEConfig.ecoCpuPushTickLimit = previous;
            }
        }
    }

    @FunctionalInterface private interface Handler { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, args) -> handler.call(method.getName(), args));
    }
}
