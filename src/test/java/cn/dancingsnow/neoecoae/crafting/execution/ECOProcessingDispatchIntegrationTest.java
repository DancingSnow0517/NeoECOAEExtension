package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern;
import net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ECOProcessingDispatchIntegrationTest {
    @Test
    void directTransportPartialAcceptanceRefundsOnlyUnownedCopiesAndStopsRamp() {
        var f = new Fixture();
        var session = mock(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.Session.class);
        var offers = new ArrayList<Long>();
        when(session.submit(any(), any(), anyLong())).thenAnswer(call -> {
            long offer = call.getArgument(2);
            offers.add(offer);
            return cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission.accepted(1, offer == 1);
        });
        try (var bridge = mockStatic(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.class)) {
            bridge.when(() -> cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.isProvider(f.provider)).thenReturn(true);
            bridge.when(() -> cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.open(f.provider)).thenReturn(session);
            var result = f.scaled(0, (request, provider) -> { fail("Direct adapter must not replay ordinary push"); return false; });
            assertEquals(List.of(1L, 2L), offers);
            assertEquals(2, result.acceptedCrafts());
            assertEquals(98, f.inventory.list.get(f.key));
            verify(f.energy).injectPower(1, Actionable.MODULATE);
            verify(f.accounting, times(2)).apply(eq(f.request), argThat(r -> r.acceptedCrafts() == 1), any(), eq(f.provider));
        }
    }

    @Test
    void directTransportExceptionRetainsDebitedInputsAndSuspendsWithoutReplay() {
        var f = new Fixture();
        var session = mock(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.Session.class);
        when(session.submit(any(), any(), anyLong())).thenThrow(new IllegalStateException("Failure after machine insertion"));
        try (var bridge = mockStatic(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.class)) {
            bridge.when(() -> cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.isProvider(f.provider)).thenReturn(true);
            bridge.when(() -> cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtDirectDispatch.open(f.provider)).thenReturn(session);
            assertNull(f.scaled(0, (request, provider) -> { fail("Uncertain ownership cannot be retried"); return false; }));
            assertEquals(99, f.inventory.list.get(f.key));
            assertTrue(f.request.job().suspended);
            verify(f.energy, never()).injectPower(anyDouble(), any());
        }
    }

    @Test
    void mekSmartQueueTakesWholeBatchBeforeThunderboltAndAccountsOnce() throws Exception {
        var f = new Fixture();
        var provider = mekProvider(f, thunderboltBatchContract());
        when(provider.getPatternAeSupport().enqueueSmartPattern(eq(f.request.pattern()), any()))
                .thenAnswer(call -> {
                    KeyCounter[] total = call.getArgument(1);
                    assertEquals(16, total[0].get(f.key));
                    return true;
                });

        var result = f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {});

        assertEquals(16, result.acceptedCrafts());
        assertEquals(84, f.inventory.list.get(f.key));
        verify(f.accounting).apply(eq(f.request), argThat(r -> r.acceptedCrafts() == 16
                && r.outputs().getFirst().amount() == 16), any(), eq(provider));
        verify(provider, never()).pushPattern(any(), any());
        assertTrue(mockingDetails(provider).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().equals("pushBatch")));
        verify(f.energy, never()).injectPower(anyDouble(), any());
    }

    @Test
    void mekQueueRejectionRefundsEntireBatchWithoutOrdinaryReplay() {
        var f = new Fixture();
        var provider = mekProvider(f);
        assertNull(f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {}));
        assertEquals(100, f.inventory.list.get(f.key));
        verify(f.energy).injectPower(16, Actionable.MODULATE);
        verifyNoInteractions(f.accounting);
        assertTrue(f.dispatcher.supports(provider, f.request.pattern()));
        verify(provider, never()).pushPattern(any(), any());
    }

    @Test
    void mekQueueFailureRetainsOwnershipAndSuspendsJob() {
        var f = new Fixture();
        var provider = mekProvider(f);
        when(provider.getPatternAeSupport().enqueueSmartPattern(any(), any()))
                .thenThrow(new IllegalStateException("save failed after enqueue"));
        assertNull(f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {}));
        assertEquals(84, f.inventory.list.get(f.key));
        assertTrue(f.request.job().suspended);
        verify(f.energy, never()).injectPower(anyDouble(), any());
        verifyNoInteractions(f.accounting);
    }

    @Test
    void mekDisabledSmartQueueDispatchesOnlyOneCopy() {
        var f = new Fixture();
        var provider = mekProvider(f);
        when(provider.isSmartPatternMultiplicationEnabled()).thenReturn(false);
        when(provider.pushPattern(eq(f.request.pattern()), any())).thenReturn(true);
        var result = f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {});
        assertEquals(1, result.acceptedCrafts());
        assertEquals(99, f.inventory.list.get(f.key));
        verify(provider.getPatternAeSupport(), never()).enqueueSmartPattern(any(), any());
    }

    @Test
    void mekInactiveOrBusyOrUnregisteredMachineDoesNotTakeInputs() {
        var f = new Fixture();
        var provider = mekProvider(f);
        var support = provider.getPatternAeSupport();
        when(support.getMainNode().isActive()).thenReturn(false);
        assertNull(f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {}));
        when(support.getMainNode().isActive()).thenReturn(true);
        when(provider.isBusy()).thenReturn(true);
        assertNull(f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {}));
        when(provider.isBusy()).thenReturn(false);
        when(support.hasRegisteredPattern(any())).thenReturn(false);
        assertNull(f.dispatcher.tryDispatch(f.request, provider, 1, f.energy, ignored -> {}));
        assertEquals(100, f.inventory.list.get(f.key));
        verify(support, never()).enqueueSmartPattern(any(), any());
    }

    private static com.beipuo.mekenergistics.blockentity.api.MeAeSupportOwner mekProvider(
            Fixture f, Class<?>... extraInterfaces) {
        var provider = mock(com.beipuo.mekenergistics.blockentity.api.MeAeSupportOwner.class,
                extraInterfaces.length == 0 ? withSettings() : withSettings().extraInterfaces(extraInterfaces));
        var support = mock(com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport.class);
        var node = mock(appeng.api.networking.IManagedGridNode.class);
        when(provider.getPatternAeSupport()).thenReturn(support);
        when(provider.isSmartPatternMultiplicationEnabled()).thenReturn(true);
        when(support.getMainNode()).thenReturn(node);
        when(node.isActive()).thenReturn(true);
        when(support.hasRegisteredPattern(f.request.pattern())).thenReturn(true);
        var input = f.request.pattern().getInputs()[0];
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(f.key, 1)});
        return provider;
    }

    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    @Test
    void ordinaryRampGrowsWithinColdVisitAndDebitsOnlyAcceptedChunks() {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        var result = f.scaled(0, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(1L, 2L, 4L, 8L, 1L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(84, f.inventory.list.get(f.key));
        verify(f.accounting, times(5)).apply(eq(f.request), any(), any(), eq(f.provider));
        offers.clear();
        var growth = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
        assertEquals(16, growth.acceptedCrafts());
    }

    @Test
    void warmedRampRefundsRejectedChunksAndStopsAfterRecovery() {
        var f = new Fixture();
        f.scaled(0, (request, provider) -> true);
        var offers = new ArrayList<Long>();
        var result = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return request.allowedCrafts() <= 2;
        });
        assertEquals(List.of(16L, 8L, 4L, 2L), offers);
        assertEquals(2, result.acceptedCrafts());
        assertEquals(82, f.inventory.list.get(f.key));
    }

    @Test
    void bufferedAcceptanceStopsGrowthAndRemainsOwned() {
        var f = new Fixture();
        f.scaled(0, (request, provider) -> true);
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList())
                .thenReturn(List.of(new GenericStack(f.key, 1)));
        var offers = new ArrayList<Long>();
        var result = f.scaled(5, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(68, f.inventory.list.get(f.key));
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList()).thenReturn(List.of());
        offers.clear();
        f.scaled(10, (request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(16L), offers);
    }

    @Test
    void nativeProviderCannotOverrideEcoOwnedBatchPolicy() throws Exception {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        Class<?> contract = thunderboltBatchContract();
        var nativeProvider = (ICraftingProvider) mock(contract, invocation -> {
            return switch (invocation.getMethod().getName()) {
                case "getBatchCapacity" -> 100L;
                case "pushBatch" -> {
                    offers.add(invocation.getArgument(2));
                    yield 6L;
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        });
        var result = f.dispatcher.tryDispatch(f.request, nativeProvider, 1, f.energy, ignored -> {});
        assertNull(result);
        assertTrue(offers.isEmpty());
        assertEquals(100L, f.inventory.list.get(f.key));
        verifyNoInteractions(f.accounting);
    }

    @Test
    void advancedProviderAcceptsEcoScaledOrdinaryPattern() {
        var f = new Fixture();
        var provider = mock(AdvPatternProviderLogic.class);
        assertTrue(ECOProcessingPatternDispatcher.supportsScaledDispatch(f.request, provider));
        f.dispatcher.beginTick(0);
        var result = f.dispatcher.tryScaledDispatch(f.request, provider, 1, f.energy, ignored -> {},
                (request, ignored) -> {
                    assertTrue(List.of(f.request.pattern()).contains(request.pattern()));
                    assertEquals(request.allowedCrafts(), request.pattern().getOutputs().getFirst().amount());
                    assertEquals(request.allowedCrafts(), request.inputs()[0].get(f.key));
                    return true;
                });
        assertEquals(16, result.acceptedCrafts());
    }

    @Test
    void advancedProcessingPatternRetainsDirectionalContractWhenScaled() throws Exception {
        var f = new Fixture();
        var provider = mock(AdvPatternProviderLogic.class);
        var pattern = mock(AdvProcessingPattern.class);
        var direction = net.minecraft.core.Direction.NORTH;
        var directions = new java.util.LinkedHashMap<AEKey, net.minecraft.core.Direction>();
        directions.put(f.key, direction);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{mock(IPatternDetails.IInput.class)});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(f.key, 1)));
        when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
        when(pattern.directionalInputsSet()).thenReturn(true);
        when(pattern.getDirectionMap()).thenReturn(directions);
        when(pattern.getDirectionSideForInputKey(f.key)).thenReturn(direction);
        var request = new ECOCraftingDispatchRequest(f.request.job(), f.request.candidate(), pattern,
                f.request.inputs(), f.request.outputs(), f.request.remainders(), f.request.allowedCrafts(),
                f.inventory, f.request.level());
        assertTrue(ECOProcessingPatternDispatcher.supportsScaledDispatch(request, provider));
        assertFalse(f.dispatcher.supports((ICraftingProvider) mock(thunderboltBatchContract()), pattern));
        f.dispatcher.beginTick(0);
        var result = f.dispatcher.tryScaledDispatch(request, provider, 1, f.energy, ignored -> {},
                (scaled, ignored) -> {
                    assertTrue(List.of(pattern).contains(scaled.pattern()));
                    assertInstanceOf(IAdvPatternDetails.class, scaled.pattern());
                    assertEquals(direction, ((IAdvPatternDetails) scaled.pattern()).getDirectionSideForInputKey(f.key));
                    assertEquals(scaled.allowedCrafts(), scaled.pattern().getOutputs().getFirst().amount());
                    return true;
                });
        assertEquals(16, result.acceptedCrafts());
    }

    @Test
    void nonMekNativeBatchContractDoesNotOverrideEcoPolicy() throws Exception {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        Class<?> contract = thunderboltBatchContract();
        var mekEnergisticsProvider = (ICraftingProvider) mock(contract, invocation -> {
            return switch (invocation.getMethod().getName()) {
                // Mek-E advertises an unbounded accounting mode, then applies its physical input
                // capacity atomically in pushBatch and returns every unaccepted copy.
                case "getBatchCapacity" -> Long.MAX_VALUE;
                case "getBatchDispatchMode" -> enumConstant(invocation.getMethod().getReturnType(), "UNBOUNDED");
                case "pushBatch" -> {
                    long offered = invocation.getArgument(2);
                    offers.add(offered);
                    yield offered - Math.min(offered, 4L);
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        });

        var result = f.dispatcher.tryDispatch(f.request, mekEnergisticsProvider, 1, f.energy, ignored -> {});

        assertNull(result);
        assertTrue(offers.isEmpty());
        assertEquals(100L, f.inventory.list.get(f.key));
        verifyNoInteractions(f.accounting);
    }

    private static Class<?> thunderboltBatchContract() throws ClassNotFoundException {
        try {
            return Class.forName("com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider");
        } catch (ClassNotFoundException legacy) {
            return Class.forName("com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    private static final class Fixture {
        final AEKey key = mock(AEKey.class);
        final IEnergyService energy = mock(IEnergyService.class);
        final ECOCraftingDispatchAccounting accounting = mock(ECOCraftingDispatchAccounting.class);
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final PatternProviderLogic provider = mock(PatternProviderLogic.class,
                withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
        final ECOProcessingPatternDispatcher dispatcher = new ECOProcessingPatternDispatcher(null,
                new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
        final ECOCraftingDispatchRequest request;

        Fixture() {
            InventoryTestBootstrap.initialize();
            var keyType = mock(AEKeyType.class);
            when(key.getType()).thenReturn(keyType);
            when(keyType.getId()).thenReturn(ResourceLocation.fromNamespaceAndPath("test", "input"));
            var pattern = mock(AEProcessingPattern.class);
            when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{mock(IPatternDetails.IInput.class)});
            when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(key, 1)));
            when(pattern.supportsPushInputsToExternalInventory()).thenReturn(true);
            when(((PatternProviderLogicAccessor) provider).neoecoae$getSendList()).thenReturn(List.of());
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(i -> i.getArgument(0));
            var inputs = new KeyCounter();
            inputs.add(key, 1);
            var plan = mock(ICraftingPlan.class);
            when(plan.finalOutput()).thenReturn(new GenericStack(key, 16));
            when(plan.emittedItems()).thenReturn(new KeyCounter());
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(java.util.UUID.randomUUID());
            ExecutingCraftingJob job;
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            }
            request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs},
                    inputs, new KeyCounter(), 16, inventory, mock(net.minecraft.world.level.Level.class));
            inventory.insert(key, 100, Actionable.MODULATE);
        }

        ECOCraftingDispatchResult scaled(long tick, ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            dispatcher.beginTick(tick);
            return dispatcher.tryScaledDispatch(request, provider, 1, energy, ignored -> {}, push);
        }
    }
}
