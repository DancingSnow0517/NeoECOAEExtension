package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.ProviderTarget;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
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

    @Test void wirelessUsesExactEcoOfferWithoutNativeCadenceOrMultiplierSettings() throws Exception {
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

    private static final class Fixture {
        final OverloadedPatternProviderLogic logic;
        final OverloadedPatternProviderBlockEntity host = mock(OverloadedPatternProviderBlockEntity.class);
        final IPatternDetails pattern = mock(IPatternDetails.class,
                withSettings().extraInterfaces(net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails.class));
        final KeyCounter[] inputs = {new KeyCounter()};
        final List<Integer> offers = new ArrayList<>();
        Fixture(boolean enabled, long accepted, boolean buffered) throws Exception {
            this(enabled, accepted, buffered, false);
        }
        Fixture(boolean enabled, long accepted, boolean buffered, boolean wireless) throws Exception {
            logic = mock(OverloadedPatternProviderLogic.class, withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
            var accessor = (PatternProviderLogicAccessor) logic;
            var level = mock(ServerLevel.class);
            when(host.getLevel()).thenReturn(level);
            when(host.getProviderMode()).thenReturn(wireless ? OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS
                    : OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL);
            when(host.isAdaptiveBatchEnabled()).thenReturn(enabled);
            var node = mock(IManagedGridNode.class);
            var grid = mock(IGrid.class);
            var energy = mock(IEnergyService.class);
            when(node.isActive()).thenReturn(true); when(node.getGrid()).thenReturn(grid);
            when(grid.getEnergyService()).thenReturn(energy);
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(c -> c.getArgument(0));
            when(logic.getCraftingLockedReason()).thenReturn(appeng.api.config.LockCraftingMode.NONE);
            when(accessor.getSendList()).thenReturn(new ArrayList<>());
            when(accessor.invokeGetActiveSides()).thenReturn(Set.of(Direction.NORTH));
            set(logic, "gridNode", node); set(logic, "overloadedHost", host);
            set(logic, "wirelessOverflow", mock(field(logic, "wirelessOverflow").getType(), call ->
                    call.getMethod().getName().equals("isEmpty") ? true : RETURNS_DEFAULTS.answer(call)));
            set(logic, "patternCatalog", mock(field(logic, "patternCatalog").getType(), call ->
                    call.getMethod().getName().equals("resolve") ? pattern : RETURNS_DEFAULTS.answer(call)));
            set(logic, "autoReturn", mock(field(logic, "autoReturn").getType()));
            Class<? extends ProviderTarget> targetClass = wireless ? OverloadedPatternProviderBlockEntity.WirelessConnection.class : ProviderTarget.class;
            var target = mock(targetClass, call -> {
                return switch (call.getMethod().getName()) {
                    case "canAccept", "supportsBatch" -> true;
                    case "boundFace" -> Direction.SOUTH;
                    case "dimension" -> net.minecraft.world.level.Level.OVERWORLD;
                    case "pos" -> net.minecraft.core.BlockPos.ZERO;
                    case "pushCopies" -> {
                        offers.add(call.getArgument(3));
                        Class<?> receipt = call.getMethod().getReturnType();
                        yield mock(receipt, r -> switch (r.getMethod().getName()) {
                            case "acceptedCopies" -> Math.toIntExact(accepted);
                            case "overflow" -> buffered ? List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)) : List.of();
                            default -> RETURNS_DEFAULTS.answer(r);
                        });
                    }
                    default -> RETURNS_DEFAULTS.answer(call);
                };
            });
            set(logic, "normalDispatch", mock(field(logic, "normalDispatch").getType(), call ->
                    call.getMethod().getName().equals("target") ? target : RETURNS_DEFAULTS.answer(call)));
            if (wireless) {
                set(logic, "validConnectionsCache", List.of(target));
                var server = mock(net.minecraft.server.MinecraftServer.class);
                when(level.getServer()).thenReturn(server);
                when(server.getLevel(net.minecraft.world.level.Level.OVERWORLD)).thenReturn(level);
                when(level.isLoaded(net.minecraft.core.BlockPos.ZERO)).thenReturn(true);
            }
            inputs[0].add(AEItemKey.of(Items.IRON_INGOT), 2);
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
