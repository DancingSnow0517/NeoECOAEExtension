package cn.dancingsnow.neoecoae.api.me;

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
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ECOProcessingDispatchIntegrationTest {
    @Test
    void ordinaryRampStartsAtOneAndDebitsOnlyAcceptedChunks() {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        var result = f.scaled((request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(1L, 1L, 2L, 4L, 8L), offers);
        assertEquals(16, result.acceptedCrafts());
        assertEquals(84, f.inventory.list.get(f.key));
        verify(f.accounting, times(5)).apply(eq(f.request), any(), any(), eq(f.provider));
    }

    @Test
    void warmedRampRefundsRejectedChunksAndStopsAfterRecovery() {
        var f = new Fixture();
        f.scaled((request, provider) -> true);
        var offers = new ArrayList<Long>();
        var result = f.scaled((request, provider) -> {
            offers.add(request.allowedCrafts());
            return request.allowedCrafts() <= 2;
        });
        assertEquals(List.of(8L, 4L, 2L), offers);
        assertEquals(2, result.acceptedCrafts());
        assertEquals(82, f.inventory.list.get(f.key));
    }

    @Test
    void bufferedAcceptanceStopsGrowthAndRemainsOwned() {
        var f = new Fixture();
        f.scaled((request, provider) -> true);
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList())
                .thenReturn(List.of(new GenericStack(f.key, 1)));
        var offers = new ArrayList<Long>();
        var result = f.scaled((request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(8L), offers);
        assertEquals(8, result.acceptedCrafts());
        assertEquals(76, f.inventory.list.get(f.key));
        when(((PatternProviderLogicAccessor) f.provider).neoecoae$getSendList()).thenReturn(List.of());
        offers.clear();
        f.scaled((request, provider) -> {
            offers.add(request.allowedCrafts());
            return true;
        });
        assertEquals(List.of(4L, 4L, 8L), offers);
    }

    @Test
    void nativeProviderReceivesFullAllowanceOnceAndRefundsLeftover() throws Exception {
        var f = new Fixture();
        var offers = new ArrayList<Long>();
        Class<?> contract = Class.forName("com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider");
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
            var keyType = mock(AEKeyType.class);
            when(key.getType()).thenReturn(keyType);
            when(keyType.getId()).thenReturn(ResourceLocation.fromNamespaceAndPath("test", "input"));
            var pattern = mock(AEProcessingPattern.class);
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
            ExecutingCraftingJob job;
            try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(plan, ignored -> {}, mock(CraftingLink.class), null);
            }
            request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs},
                    inputs, new KeyCounter(), 16, inventory, null);
            inventory.insert(key, 100, Actionable.MODULATE);
        }

        ECOCraftingDispatchResult scaled(ECOCraftingProviderDispatcher.ECOCraftingNormalPush push) {
            return dispatcher.tryScaledDispatch(request, provider, 1, energy, ignored -> {}, push);
        }
    }
}
