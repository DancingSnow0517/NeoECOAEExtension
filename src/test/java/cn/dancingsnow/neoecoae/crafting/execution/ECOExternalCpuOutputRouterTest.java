package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.output.ECOExternalCpuOutputRouter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOExternalCpuOutputRouterTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(longs = {0, 3, 7})
    void nativeCpuRetainsAllDirectedIntermediateOutputsIncludingSurplus(long expected) throws Exception {
        var fixture = fixture(expected);
        var intermediate = AEItemKey.of(Items.IRON_INGOT);
        assertEquals(7, ECOExternalCpuOutputRouter.insert(
            fixture.cpu, fixture.jobId, intermediate, 7, Actionable.MODULATE));
        assertEquals(7, fixture.logic.getInventory().list.get(intermediate));
        assertEquals(0, fixture.logic.getWaitingFor(intermediate));
        assertSame(fixture.link, fixture.logic.getLastLink());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 3})
    void nativeFinalOutputRetainsRequesterRejectionAndSurplusAfterJobFinishes(long requesterAccepted) throws Exception {
        var fixture = fixture(0);
        var result = AEItemKey.of(Items.DIAMOND);
        fixture.waiting.insert(result, 3, Actionable.MODULATE);
        when(fixture.link.insert(result, 3, Actionable.MODULATE)).thenReturn(requesterAccepted);

        assertEquals(7, ECOExternalCpuOutputRouter.insert(
            fixture.cpu, fixture.jobId, result, 7, Actionable.MODULATE));
        assertNull(fixture.logic.getLastLink());
        assertEquals(7 - requesterAccepted, fixture.logic.getInventory().list.get(result));
        verify(fixture.link).markDone();
    }

    @Test
    void simulationAccountsForCpuBufferWithoutChangingWaitingOrInventory() throws Exception {
        var fixture = fixture(3);
        var intermediate = AEItemKey.of(Items.IRON_INGOT);
        assertEquals(7, ECOExternalCpuOutputRouter.insert(
            fixture.cpu, fixture.jobId, intermediate, 7, Actionable.SIMULATE));
        assertEquals(3, fixture.logic.getWaitingFor(intermediate));
        assertTrue(fixture.logic.getInventory().list.isEmpty());
        verify(fixture.cpu, never()).markDirty();
    }

    @Test
    void anotherJobCannotReceiveTheWorkstationsOutputs() throws Exception {
        var fixture = fixture(7);
        var intermediate = AEItemKey.of(Items.IRON_INGOT);
        assertEquals(0, ECOExternalCpuOutputRouter.insert(
            fixture.cpu, UUID.randomUUID(), intermediate, 7, Actionable.MODULATE));
        assertEquals(7, fixture.logic.getWaitingFor(intermediate));
        assertTrue(fixture.logic.getInventory().list.isEmpty());
    }

    private static Fixture fixture(long intermediateWaiting) throws Exception {
        var cpu = mock(CraftingCPUCluster.class);
        var logic = new CraftingCpuLogic(cpu);
        field(cpu, "craftingLogic", logic);
        var jobId = UUID.randomUUID();
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(jobId);
        var waiting = new ListCraftingInventory(ignored -> {});
        waiting.insert(AEItemKey.of(Items.IRON_INGOT), intermediateWaiting, Actionable.MODULATE);
        var job = mock(ExecutingCraftingJob.class);
        field(job, "link", link);
        field(job, "waitingFor", waiting);
        field(job, "tasks", Map.of());
        field(job, "timeTracker", mock(ElapsedTimeTracker.class));
        field(job, "finalOutput", new GenericStack(AEItemKey.of(Items.DIAMOND), 3));
        field(job, "remainingAmount", 3L);
        field(logic, "job", job);
        return new Fixture(cpu, logic, jobId, link, waiting);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        var field = (target instanceof CraftingCPUCluster ? CraftingCPUCluster.class
            : target instanceof CraftingCpuLogic ? CraftingCpuLogic.class : ExecutingCraftingJob.class)
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(CraftingCPUCluster cpu, CraftingCpuLogic logic, UUID jobId,
                           CraftingLink link, ListCraftingInventory waiting) {}
}
