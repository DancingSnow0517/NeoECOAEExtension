package cn.dancingsnow.neoecoae.crafting.planner.compiled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompiledStructureCacheTest {
    private static AEKey raw, goal;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        raw = AEItemKey.of(Items.RAW_IRON);
        goal = AEItemKey.of(Items.IRON_INGOT);
    }

    @Test void separateServicesAndSessionsReuseStructureButCaptureFreshStockAndAmounts() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(5)).plan(5, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.MISSING_ITEMS, new ECOCraftingPlannerService().createSession(service, goal, stock(0))
                .plan(1, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(2)).plan(2, false, ECOCancellation.NONE).status());
        verify(service, times(1)).getCraftingFor(goal);
        verify(service, times(1)).getCraftingFor(raw);
    }

    @Test void providerRefreshInvalidatesTheCompiledContract() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(2L);
        when(service.getCraftingFor(goal)).thenReturn(List.of());
        assertEquals(PlanningStatus.MISSING_ITEMS, planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void pendingProviderRebuildCannotReadOrPopulateTheCache() throws Exception {
        var service = service(true);
        var revision = (ECOCraftingProviderRevision) service;
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(false);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(true);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test void cycleCapabilityAndComponentMatchingUseSeparateCacheEntries() throws Exception {
        var service = service(true);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5), false).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5), true).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5), false, false, Set.of(BuiltInRegistries.ITEM.getKey(Items.RAW_IRON)))
                .plan(1, false, ECOCancellation.NONE);
        verify(service, times(3)).getCraftingFor(goal);
    }

    @Test void servicesWithoutStableRevisionEvidenceAlwaysCompileFresh() throws Exception {
        var service = service(false);
        var planner = new ECOCraftingPlannerService();
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        planner.createSession(service, goal, stock(5)).plan(1, false, ECOCancellation.NONE);
        verify(service, times(2)).getCraftingFor(goal);
    }

    @Test void twoNetworksWithTheSameRevisionNeverSharePhysicalPatterns() throws Exception {
        var first = service(true);
        var second = service(true);
        when(second.getCraftingFor(goal)).thenReturn(List.of());
        var planner = new ECOCraftingPlannerService();
        assertEquals(PlanningStatus.SUCCESS, planner.createSession(first, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.MISSING_ITEMS, planner.createSession(second, goal, stock(5)).plan(1, false, ECOCancellation.NONE).status());
        verify(second).getCraftingFor(goal);
    }

    private static KeyCounter stock(long amount) {
        var stock = new KeyCounter();
        if (amount > 0) stock.add(raw, amount);
        return stock;
    }

    private static ICraftingService service(boolean revisioned) {
        var service = revisioned ? mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingProviderRevision.class))
                : mock(ICraftingService.class);
        if (revisioned) {
            when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(1L);
            when(((ECOCraftingProviderRevision) service).neoecoae$isProviderSnapshotStable()).thenReturn(true);
        }
        var input = mock(IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(raw, 1)});
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] {input});
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(goal, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(goal, 1));
        when(service.getCraftingFor(goal)).thenReturn(List.of(pattern));
        when(service.getCraftingFor(raw)).thenReturn(List.of());
        return service;
    }
}
