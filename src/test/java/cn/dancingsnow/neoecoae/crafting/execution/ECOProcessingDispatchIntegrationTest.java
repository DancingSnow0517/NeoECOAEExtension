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
    void nativeProviderReceivesFullAllowanceOnceAndRefundsLeftover() throws Exception {
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
        assertEquals(List.of(16L), offers);
        assertEquals(10, result.acceptedCrafts());
        assertEquals(90, f.inventory.list.get(f.key));
        verify(f.energy).injectPower(6, Actionable.MODULATE);
        verify(f.accounting).apply(eq(f.request), argThat(r -> r.acceptedCrafts() == 10), any(), eq(nativeProvider));
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
        assertTrue(f.dispatcher.supports((ICraftingProvider) mock(thunderboltBatchContract()), pattern));
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
    void mekEnergisticsSmartMultiplicationUsesThunderboltAndSettlesPhysicalAcceptance() throws Exception {
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

        assertEquals(List.of(16L), offers);
        assertEquals(4L, result.acceptedCrafts());
        assertEquals(96L, f.inventory.list.get(f.key));
        verify(f.energy).injectPower(12, Actionable.MODULATE);
        verify(f.accounting).apply(eq(f.request), argThat(r -> r.acceptedCrafts() == 4), any(),
                eq(mekEnergisticsProvider));
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
