package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.provider.ProviderTarget;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

class ECOAe2LtDirectDispatchTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void optionalContractResolvesAgainstReleasedJarAndIgnoresAdaptiveSwitch() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            var f = new Fixture(enabled, 8, false);
            var session = ECOAe2LtDirectDispatch.open(f.logic);
            assertNotNull(session, "All reflected transport members must exist in the released AE2LT jar");
            assertEquals(Integer.MAX_VALUE, session.maxBatchSize(f.pattern));
            var receipt = session.submit(f.pattern, f.inputs, 8);
            assertEquals(8, receipt.acceptedCrafts());
            assertTrue(receipt.canContinue());
            verify(f.host, never()).isAdaptiveBatchEnabled();
            verify(f.logic, never()).getBatchCapacity(any());
            verify(f.logic, never()).pushBatch(any(), any(), anyLong());
            assertEquals(List.of(8), f.offers);
        }
    }

    @Test void partialOrBufferedAcceptanceNeverProvesLargerCapacity() throws Exception {
        for (boolean buffered : List.of(false, true)) {
            var f = new Fixture(false, buffered ? 8 : 3, buffered);
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic)).submit(f.pattern, f.inputs, 8);
            assertEquals(buffered ? 8 : 3, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue());
            assertEquals(List.of(8), f.offers);
        }
    }

    @Test void rejectionDoesNotInvokeSuccessCallback() throws Exception {
        var f = new Fixture(false, 0, false);
        var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic)).submit(f.pattern, f.inputs, 8);
        assertEquals(0, receipt.acceptedCrafts());
        verify((PatternProviderLogicAccessor) f.logic, never()).invokeOnPushPatternSuccess(any());
    }

    @Test void wirelessUsesExactEcoOfferWithoutNativeAdaptiveRampOrMultiplierSettings() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            var f = new Fixture(enabled, 8, false, true);
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic)).submit(f.pattern, f.inputs, 8);
            assertEquals(8, receipt.acceptedCrafts());
            assertTrue(receipt.canContinue());
            assertEquals(List.of(8), f.offers);
            verify(f.host, never()).isAdaptiveBatchEnabled();
            assertTrue(mockingDetails(f.host).getInvocations().stream()
                    .noneMatch(call -> call.getMethod().getName().equals("getMachineParallelism")));
            verify(f.logic, never()).pushBatch(any(), any(), anyLong());
        }
    }

    @Test void growingBatchesAreSharedAcrossTargetsWithinTheSameTick() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            for (long copies : List.of(1L, 2L, 4L, 8L, 16L, 32L)) {
                // Each CPU visit opens a new session. Fairness must belong to the provider.
                var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                        .submit(f.pattern, f.inputs, copies);
                assertEquals(copies, receipt.acceptedCrafts());
                assertTrue(receipt.canContinue());
            }
            assertArrayEquals(new long[]{21, 21, 21}, f.received,
                    "Same-tick batch growth must not repeatedly feed the same target: wireless=" + wireless);
            verify(f.host, never()).isAdaptiveBatchEnabled();
            verify(f.logic, never()).pushBatch(any(), any(), anyLong());
        }
    }

    @Test void sameTickSingleCopiesRotateAcrossTargets() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, 1, false, wireless, 3);
            for (int i = 0; i < 12; i++) {
                var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                        .submit(f.pattern, f.inputs, 1);
                assertEquals(1, receipt.acceptedCrafts());
            }
            assertArrayEquals(new long[]{4, 4, 4}, f.received);
        }
    }

    @Test void unavailableTargetsDoNotSuppressOtherTargetsOrLoseAcceptedCopies() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            f.capacities[0] = 0;
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, 12);
            assertEquals(12, receipt.acceptedCrafts());
            assertArrayEquals(new long[]{0, 6, 6}, f.received);
        }
    }

    @Test void partialTargetAcceptanceIsCountedAcrossTheWholeBatch() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            f.capacities[0] = 2;
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, 12);
            assertEquals(12, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue(), "A partial target cannot prove a larger batch is safe");
            assertArrayEquals(new long[]{2, 5, 5}, f.received);
        }
    }

    @Test void bufferedInputsStopBeforeAnotherTargetTakesOwnership() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, true, wireless, 3);
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, 12);
            assertEquals(4, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue());
            assertArrayEquals(new long[]{4, 0, 0}, f.received);
        }
    }

    @Test void wirelessSingleTargetModeKeepsTheNativeTargetChoice() throws Exception {
        var f = new Fixture(false, Integer.MAX_VALUE, false, true, 3);
        when(f.host.getWirelessDispatchMode()).thenReturn(
                OverloadedPatternProviderBlockEntity.WirelessDispatchMode.SINGLE_TARGET);
        for (long copies : List.of(1L, 2L, 4L, 8L)) {
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, copies);
            assertEquals(copies, receipt.acceptedCrafts());
        }
        assertArrayEquals(new long[]{15, 0, 0}, f.received);
    }

    @Test void powerFailureAfterOneTargetPreservesItsAcceptedCopies() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            when(f.energy.extractAEPower(anyDouble(), eq(appeng.api.config.Actionable.SIMULATE), any()))
                    .thenAnswer(call -> Arrays.stream(f.received).sum() == 0 ? call.getArgument(0) : 0D);
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, 12);
            assertEquals(4, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue());
            assertArrayEquals(new long[]{4, 0, 0}, f.received);
        }
    }

    @Test void machinesWithoutCountedTransportReceiveOnlyOneCopyEach() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            for (var target : f.targets) when(target.supportsBatch(any(), any())).thenReturn(false);
            var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                    .submit(f.pattern, f.inputs, 12);
            assertEquals(3, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue());
            assertArrayEquals(new long[]{1, 1, 1}, f.received);
        }
    }

    @Test void wirelessFairnessBelongsToEachCanonicalPattern() throws Exception {
        var f = new Fixture(false, Integer.MAX_VALUE, false, true, 3);
        var otherPattern = mock(IPatternDetails.class);
        assertEquals(1, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(f.pattern, f.inputs, 1).acceptedCrafts());
        assertEquals(1, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(otherPattern, f.inputs, 1).acceptedCrafts());
        assertArrayEquals(new long[]{2, 0, 0}, f.received,
                "A different recipe must not inherit the first recipe's target counts");
    }

    @Test void directionalBatchesPreserveOneCopyRoutingAndStopOnFirstRejection() throws Exception {
        var f = new Fixture(false, 0, false);
        when(((net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails) f.pattern).directionalInputsSet()).thenReturn(true);
        assertEquals(1, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic)).maxBatchSize(f.pattern));
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        when(f.logic.pushPattern(eq(f.pattern), any())).thenAnswer(call -> {
            KeyCounter[] inputs = call.getArgument(1);
            assertEquals(2, inputs[0].get(AEItemKey.of(Items.IRON_INGOT)));
            return attempts.incrementAndGet() <= 3;
        });
        var receipt = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic)).submit(f.pattern, f.inputs, 8);
        assertEquals(3, receipt.acceptedCrafts());
        assertFalse(receipt.canContinue());
        assertEquals(4, attempts.get());
        assertTrue(f.offers.isEmpty(), "Routed inputs cannot go through the non-directional chunk adapter");
        verify(f.host, never()).isAdaptiveBatchEnabled();
    }

    @Test void postAcceptanceSaveFailureIsNotConvertedToRejection() throws Exception {
        var f = new Fixture(false, 8, false);
        doThrow(new IllegalStateException("save after insertion")).when(f.logic).saveChanges();
        assertThrows(IllegalStateException.class, () -> Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(f.pattern, f.inputs, 8));
        assertEquals(List.of(8), f.offers);
        verify((PatternProviderLogicAccessor) f.logic).invokeOnPushPatternSuccess(f.pattern);
    }

    @Test void samePatternBlockingKeepsNativeChecksAndCanonicalRecipeIdentity() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless);
            when(f.logic.isBlocking()).thenReturn(true);
            when(f.host.getBlockingMode()).thenReturn(OverloadedPatternProviderBlockEntity.BlockingMode.SAME_PATTERN);
            assertTrue(ECOExternalProviderBlocking.isEnabled(f.logic), "Other batch paths must remain blocked");
            assertTrue(ECOAe2LtDirectDispatch.canBatchWhileBlocking(f.logic));
            var session = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic));
            assertEquals(8, session.submit(f.pattern, f.inputs, 8).acceptedCrafts());

            var sameRecipeView = mock(IPatternDetails.class);
            f.canonicalPatterns.put(sameRecipeView, f.pattern);
            assertEquals(8, session.submit(sameRecipeView, f.inputs, 8).acceptedCrafts());
            assertEquals(List.of(8, 8), f.offers);
            assertTrue(f.blockingChecks.stream().allMatch(check ->
                    check.pattern() == f.pattern && check.blocking() && check.samePattern()));

            var differentRecipe = mock(IPatternDetails.class);
            f.blockedPatterns.add(differentRecipe);
            assertEquals(0, session.submit(differentRecipe, f.inputs, 8).acceptedCrafts());
            assertEquals(List.of(8, 8), f.offers, "A refused recipe must not reach material transfer");
            assertSame(differentRecipe, f.blockingChecks.getLast().pattern());
            assertArrayEquals(new long[]{16}, f.received);
        }
    }

    @Test void ordinaryOrUnrecognizedBlockingNeverStartsDirectBatch() throws Exception {
        for (var mode : Arrays.asList(OverloadedPatternProviderBlockEntity.BlockingMode.NORMAL, null)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false);
            when(f.logic.isBlocking()).thenReturn(true);
            when(f.host.getBlockingMode()).thenReturn(mode);
            assertFalse(ECOAe2LtDirectDispatch.canBatchWhileBlocking(f.logic));
            var session = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic));
            assertEquals(0, session.submit(f.pattern, f.inputs, 8).acceptedCrafts());
            assertTrue(f.offers.isEmpty());
        }
    }

    @Test void unreadableBlockingModeFallsBackWithoutTakingMaterials() throws Exception {
        var f = new Fixture(false, Integer.MAX_VALUE, false);
        when(f.logic.isBlocking()).thenReturn(true);
        when(f.host.getBlockingMode()).thenThrow(new IllegalStateException("Mode unavailable"));
        assertFalse(ECOAe2LtDirectDispatch.canBatchWhileBlocking(f.logic));
        assertEquals(0, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(f.pattern, f.inputs, 8).acceptedCrafts());
        assertTrue(f.offers.isEmpty());
    }

    @Test void unreadableBlockingSwitchDoesNotGrantSamePatternPermission() throws Exception {
        var f = new Fixture(false, Integer.MAX_VALUE, false);
        when(f.logic.isBlocking()).thenThrow(new IllegalStateException("Blocking switch unavailable"));
        when(f.host.getBlockingMode()).thenReturn(OverloadedPatternProviderBlockEntity.BlockingMode.SAME_PATTERN);
        assertFalse(ECOAe2LtDirectDispatch.canBatchWhileBlocking(f.logic));
        assertEquals(0, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(f.pattern, f.inputs, 8).acceptedCrafts());
        assertTrue(f.offers.isEmpty());
    }

    @Test void switchingToOrdinaryBlockingStopsBeforeTheNextPhysicalTarget() throws Exception {
        for (boolean wireless : List.of(false, true)) {
            var f = new Fixture(false, Integer.MAX_VALUE, false, wireless, 3);
            when(f.logic.isBlocking()).thenReturn(true);
            when(f.host.getBlockingMode()).thenReturn(OverloadedPatternProviderBlockEntity.BlockingMode.SAME_PATTERN);
            doAnswer(call -> {
                when(f.host.getBlockingMode()).thenReturn(OverloadedPatternProviderBlockEntity.BlockingMode.NORMAL);
                return null;
            }).when(f.logic).saveChanges();
            var session = Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic));
            var receipt = session.submit(f.pattern, f.inputs, 12);
            assertEquals(4, receipt.acceptedCrafts());
            assertFalse(receipt.canContinue());
            assertArrayEquals(new long[]{4, 0, 0}, f.received);
            assertEquals(0, session.submit(f.pattern, f.inputs, 12).acceptedCrafts());
            assertEquals(List.of(4), f.offers);
        }
    }

    @Test void craftingLockStillRejectsSamePatternBatches() throws Exception {
        var f = new Fixture(false, Integer.MAX_VALUE, false);
        when(f.logic.isBlocking()).thenReturn(true);
        when(f.host.getBlockingMode()).thenReturn(OverloadedPatternProviderBlockEntity.BlockingMode.SAME_PATTERN);
        when(f.logic.getCraftingLockedReason()).thenReturn(Arrays.stream(appeng.api.config.LockCraftingMode.values())
                .filter(mode -> mode != appeng.api.config.LockCraftingMode.NONE).findFirst().orElseThrow());
        assertEquals(0, Objects.requireNonNull(ECOAe2LtDirectDispatch.open(f.logic))
                .submit(f.pattern, f.inputs, 8).acceptedCrafts());
        assertTrue(f.offers.isEmpty());
    }

    private record BlockingCheck(IPatternDetails pattern, boolean blocking, boolean samePattern) {}

    private static final class Fixture {
        final OverloadedPatternProviderLogic logic;
        final OverloadedPatternProviderBlockEntity host = mock(OverloadedPatternProviderBlockEntity.class);
        final IPatternDetails pattern = mock(IPatternDetails.class,
                withSettings().extraInterfaces(net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails.class));
        final KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
        final List<Integer> offers = new ArrayList<>();
        final List<ProviderTarget> targets = new ArrayList<>();
        final IEnergyService energy = mock(IEnergyService.class);
        final Map<IPatternDetails, IPatternDetails> canonicalPatterns = new IdentityHashMap<>();
        final ReferenceSet<IPatternDetails> blockedPatterns = new ReferenceOpenHashSet<>();
        final List<BlockingCheck> blockingChecks = new ArrayList<>();
        final long[] capacities;
        final long[] received;
        Fixture(boolean enabled, long accepted, boolean buffered) throws Exception {
            this(enabled, accepted, buffered, false);
        }
        Fixture(boolean enabled, long accepted, boolean buffered, boolean wireless) throws Exception {
            this(enabled, accepted, buffered, wireless, 1);
        }
        Fixture(boolean enabled, long accepted, boolean buffered, boolean wireless, int targetCount) throws Exception {
            capacities = new long[targetCount];
            Arrays.fill(capacities, accepted);
            received = new long[targetCount];
            logic = mock(OverloadedPatternProviderLogic.class, withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
            var accessor = (PatternProviderLogicAccessor) logic;
            var level = mock(ServerLevel.class);
            when(host.getLevel()).thenReturn(level);
            when(host.getProviderMode()).thenReturn(wireless ? OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS
                    : OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL);
            when(host.isAdaptiveBatchEnabled()).thenReturn(enabled);
            when(host.getWirelessDispatchMode()).thenReturn(
                    OverloadedPatternProviderBlockEntity.WirelessDispatchMode.EVEN_DISTRIBUTION);
            when(host.getWirelessSpeedMode()).thenReturn(
                    OverloadedPatternProviderBlockEntity.WirelessSpeedMode.FAST);
            when(level.getGameTime()).thenReturn(100L);
            var node = mock(IManagedGridNode.class);
            var grid = mock(IGrid.class);
            when(node.isActive()).thenReturn(true); when(node.getGrid()).thenReturn(grid);
            when(grid.getEnergyService()).thenReturn(energy);
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(c -> c.getArgument(0));
            when(logic.getCraftingLockedReason()).thenReturn(appeng.api.config.LockCraftingMode.NONE);
            var sendList = new ArrayList<GenericStack>();
            when(accessor.getSendList()).thenReturn(sendList);
            doAnswer(call -> {
                sendList.add(new GenericStack(call.getArgument(0), call.getArgument(1)));
                return null;
            }).when(accessor).invokeAddToSendList(any(), anyLong());
            var directions = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
                    Direction.WEST, Direction.UP, Direction.DOWN).subList(0, targetCount);
            when(accessor.invokeGetActiveSides()).thenReturn(new LinkedHashSet<>(directions));
            set(logic, "gridNode", node); set(logic, "overloadedHost", host);
            set(logic, "wirelessOverflow", mock(field(logic, "wirelessOverflow").getType(), call ->
                    call.getMethod().getName().equals("isEmpty") ? true : RETURNS_DEFAULTS.answer(call)));
            set(logic, "patternCatalog", mock(field(logic, "patternCatalog").getType(), call ->
                    call.getMethod().getName().equals("resolve")
                            ? canonicalPatterns.getOrDefault(call.getArgument(0), call.getArgument(0))
                            : RETURNS_DEFAULTS.answer(call)));
            set(logic, "autoReturn", mock(field(logic, "autoReturn").getType()));
            Class<? extends ProviderTarget> targetClass = wireless ? OverloadedPatternProviderBlockEntity.WirelessConnection.class : ProviderTarget.class;
            for (int index = 0; index < targetCount; index++) {
                int targetIndex = index;
                var target = mock(targetClass, call -> {
                    return switch (call.getMethod().getName()) {
                        case "canAccept", "supportsBatch", "isAlive" -> true;
                        case "boundFace" -> Direction.SOUTH;
                        case "dimension" -> net.minecraft.world.level.Level.OVERWORLD;
                        case "pos" -> new net.minecraft.core.BlockPos(targetIndex, 0, 0);
                        case "isBlocked" -> {
                            IPatternDetails canonical = call.getArgument(2);
                            blockingChecks.add(new BlockingCheck(canonical, call.getArgument(4), call.getArgument(5)));
                            yield blockedPatterns.contains(canonical);
                        }
                        case "pushCopies" -> {
                            int copies = call.getArgument(3);
                            offers.add(copies);
                            KeyCounter[] prototype = call.getArgument(2);
                            assertEquals(2, prototype[0].get(AEItemKey.of(Items.IRON_INGOT)),
                                    "Each target receives an unscaled one-copy prototype");
                            assertEquals(3, prototype[1].get(AEItemKey.of(Items.GOLD_INGOT)),
                                    "Every ingredient must retain the same recipe ratio");
                            int owned = Math.toIntExact(Math.min(capacities[targetIndex], copies));
                            received[targetIndex] += owned;
                            Class<?> receipt = call.getMethod().getReturnType();
                            yield mock(receipt, r -> switch (r.getMethod().getName()) {
                                case "acceptedCopies" -> owned;
                                case "overflow" -> buffered
                                        ? List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)) : List.of();
                                default -> RETURNS_DEFAULTS.answer(r);
                            });
                        }
                        default -> RETURNS_DEFAULTS.answer(call);
                    };
                });
                targets.add(target);
            }
            set(logic, "normalDispatch", mock(field(logic, "normalDispatch").getType(), call ->
                    switch (call.getMethod().getName()) {
                        case "target" -> targets.get(directions.indexOf(call.getArgument(2)));
                        case "dispatchOrder" -> call.callRealMethod();
                        default -> RETURNS_DEFAULTS.answer(call);
                    }));
            if (wireless) {
                var schedulerType = field(logic, "wirelessDispatch").getType();
                var constructor = schedulerType.getDeclaredConstructor();
                constructor.setAccessible(true);
                var scheduler = constructor.newInstance();
                set(logic, "wirelessDispatch", scheduler);
                set(logic, "wirelessOverflow", field(scheduler, "overflow").get(scheduler));
                set(logic, "validConnectionsCache", List.copyOf(targets));
                set(logic, "validConnectionsCacheTick", 100L);
                var server = mock(net.minecraft.server.MinecraftServer.class);
                when(level.getServer()).thenReturn(server);
                when(server.getLevel(net.minecraft.world.level.Level.OVERWORLD)).thenReturn(level);
                when(level.isLoaded(any(net.minecraft.core.BlockPos.class))).thenReturn(true);
            }
            inputs[0].add(AEItemKey.of(Items.IRON_INGOT), 2);
            inputs[1].add(AEItemKey.of(Items.GOLD_INGOT), 3);
        }
    }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
}
