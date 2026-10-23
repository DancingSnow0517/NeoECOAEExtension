package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.output.ECOExternalCpuOutputRouter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.crafting.matrix.core.CopyAssembler;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreHost;
import com.moakiee.ae2lt.crafting.matrix.core.CraftingCoreRegistry;
import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.logic.craft.MatrixCraftCore;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingCluster;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingEnergy;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises existing ordinary dispatch independently of the unused capability adapter. */
class ECOAe2LtExistingInteropTest {
    @Test
    void releasedTimeWheelOrdinaryDispatchPreservesTheActiveJobId() throws Exception {
        var id = UUID.randomUUID();
        var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
        var jobField = Ae2LtTimeWheelCraftingCpuLogic.class.getDeclaredField("job");
        jobField.setAccessible(true);
        var active = mock(jobField.getType());
        var linkField = jobField.getType().getDeclaredField("link");
        linkField.setAccessible(true);
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(id);
        linkField.set(active, link);
        jobField.set(logic, active);
        var provider = mock(cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider.class);
        var pattern = mock(IPatternDetails.class);
        var inputs = new KeyCounter[] {new KeyCounter()};
        when(provider.pushPattern(pattern, inputs, id)).thenReturn(true);
        assertTrue(cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtJobDispatch.push(logic, provider, pattern, inputs,
            () -> provider.pushPattern(pattern, inputs)));
        verify(provider).pushPattern(pattern, inputs, id);
        verify(provider, never()).pushPattern(pattern, inputs);
    }

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ordinaryEcoDispatchUsesMatrixAndRegistersWaitingBeforeFiveTickReturn(boolean formed) throws Exception {
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var pattern = mock(IMolecularAssemblerSupportedPattern.class);
        var slot = mock(IPatternDetails.IInput.class);
        when(slot.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(input, 1)});
        when(slot.getMultiplier()).thenReturn(1L);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.STONE));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{slot});
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        var plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 1));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 1L));
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        ExecutingCraftingJob job;
        try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
        }

        var clock = new AtomicLong(100);
        var returned = new KeyCounter();
        var host = mock(CraftingCoreHost.class);
        when(host.getGameTime()).thenAnswer(call -> clock.get());
        when(host.isConnected()).thenReturn(true);
        when(host.insertToNetwork(any(), anyLong())).thenAnswer(call -> {
            assertEquals(1, job.waitingFor.list.get(output), "ECO must commit its waiting ledger first");
            returned.add(call.getArgument(0), call.getArgument(1));
            return call.getArgument(1);
        });
        var cores = new MatrixCraftCore() {
            @Override
            public List<MatrixCraftingUnit> craftingUnits() {
                return List.of(MatrixCraftingUnit.stableCore(), MatrixCraftingUnit.t1Threader());
            }
        };
        var registry = new CraftingCoreRegistry();
        var cluster = new MatrixCraftingCluster(() -> formed, List.of(() -> List.of(pattern)),
                List.of(cores), host,
                (details, inputs) -> new CopyAssembler.AssembledCopy(output, 1, List.of()),
                registry, MatrixCraftingEnergy.UNLIMITED);
        // World discovery and recipe assembly are fixtures; the port entry, matrix engine,
        // ECO material transaction and waiting ledger below execute production code.
        var controller = mock(MatrixControllerBlockEntity.class);
        when(controller.pushPattern(eq(pattern), any())).thenAnswer(call ->
                cluster.pushSingle(call.getArgument(0), call.getArgument(1)));
        var port = mock(MatrixPortBlockEntity.class);
        when(port.getController()).thenReturn(controller);
        doCallRealMethod().when(port).pushPattern(any(), any());

        var inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(input, 1, Actionable.MODULATE);
        var inputs = new KeyCounter();
        inputs.add(input, 1);
        var outputs = new KeyCounter();
        outputs.add(output, 1);
        var request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[]{inputs},
                outputs, new KeyCounter(), 1, inventory, mock(Level.class));
        var energy = mock(IEnergyService.class);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        var accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {},
                active -> new ECOCraftingJobContext(mock(ICraftingCPU.class), link.getCraftingID(),
                        active.finalOutput, 1, active.remainingAmount), event -> {});
        var dispatcher = new ECOCraftingProviderDispatcher(null, mock(ECOCraftingFastPathDispatcher.class),
                new ECOCraftingEnergyTransaction(() -> {}, () -> 0), accounting);
        var owner = mock(ECOCraftingCPULogic.class);
        var normalPush = ECOCraftingCPULogic.class.getDeclaredMethod("invokeNormalProvider",
                ECOCraftingDispatchRequest.class, ICraftingProvider.class);
        normalPush.setAccessible(true);
        dispatcher.beginTick(100);
        var result = dispatcher.dispatchCandidate(request, List.of(port),
                new ECOCraftingDispatchBudget(4, 4), energy, mock(ECODispatchStallDiagnostics.class),
                ignored -> {}, () -> {}, (single, provider) -> {
                    try {
                        return (boolean) normalPush.invoke(owner, single, provider);
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                });

        assertEquals(formed ? 1 : 0, result.acceptedCrafts());
        assertEquals(formed ? 0 : 1, inventory.list.get(input));
        assertEquals(formed ? 1 : 0, job.waitingFor.list.get(output));
        assertEquals(formed ? 0 : 1, job.tasks.get(pattern).value);
        verify(port).pushPattern(eq(pattern), any());
        verify(port, never()).inspect(any());
        verify(port, never()).submit(any());
        verify(port, never()).pushBatch(any(), any(), anyLong());
        assertTrue(returned.isEmpty());
        clock.set(104);
        registry.tickAll();
        assertTrue(returned.isEmpty());
        clock.set(105);
        registry.tickAll();
        assertEquals(formed ? 1 : 0, returned.get(output));
    }

    @Test
    void jobDirectedOutputRouterUsesTianshuPublicApiAndChecksTheActiveJob() {
        var cpu = mock(TimeWheelCraftingCPU.class);
        var logic = mock(Ae2LtTimeWheelCraftingCpuLogic.class);
        var link = mock(CraftingLink.class);
        var jobId = UUID.randomUUID();
        var output = AEItemKey.of(Items.DIAMOND);
        when(cpu.getCraftingLogic()).thenReturn(logic);
        when(logic.getLastLink()).thenReturn(link);
        when(link.getCraftingID()).thenReturn(jobId);
        when(logic.getWaitingFor(output)).thenReturn(3L);
        when(logic.insert(output, 3, Actionable.MODULATE)).thenReturn(3L);

        assertEquals(0, ECOExternalCpuOutputRouter.insert(cpu, UUID.randomUUID(), output, 9, Actionable.MODULATE));
        verify(logic, never()).insert(any(), anyLong(), any());
        assertEquals(3, ECOExternalCpuOutputRouter.insert(cpu, jobId, output, 9, Actionable.MODULATE));
        verify(logic).insert(output, 3, Actionable.MODULATE);
    }
}
