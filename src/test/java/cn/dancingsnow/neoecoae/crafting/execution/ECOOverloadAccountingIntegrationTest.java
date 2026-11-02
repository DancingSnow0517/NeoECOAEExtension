package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOOverloadCpuAccountingBridge;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.model.*;
import com.moakiee.ae2lt.overload.runtime.pattern.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real ECO insertion, public AE2LT registration, component variants and per-job persistence. */
class ECOOverloadAccountingIntegrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactAndSubstitutedReturnsRetireBothLedgersExactlyOnce(boolean substituted) {
        var f = fixture(false, 7);
        f.register(7);
        var incoming = substituted ? f.actual : f.expected;
        assertEquals(3, f.logic.insert(incoming, 3, Actionable.SIMULATE));
        assertEquals(7, f.job.waitingFor.list.get(f.expected));
        assertEquals(7, pending(f));
        assertEquals(0, f.logic.getInventory().list.get(incoming));
        assertEquals(3, f.logic.insert(incoming, 3, Actionable.MODULATE));
        assertEquals(4, f.job.waitingFor.list.get(f.expected));
        assertEquals(4, pending(f));
        assertEquals(3, f.logic.getInventory().list.get(incoming));
        assertEquals(4, f.logic.insert(incoming, 99, Actionable.MODULATE));
        assertEquals(0, pending(f));
        assertEquals(0, f.job.waitingFor.list.get(f.expected));
        assertEquals(7, f.logic.getInventory().list.get(incoming));
        assertEquals(0, f.logic.insert(incoming, 1, Actionable.MODULATE));
    }

    @Test void unrelatedItemsAndWrongJobCannotClaimRegisteredOutput() {
        var f = fixture(false, 4);
        f.register(4);
        assertEquals(0, f.logic.insert(AEItemKey.of(Items.STICK), 4, Actionable.MODULATE));
        assertEquals(0, ECOOverloadCpuAccountingBridge.claim(f.logic, UUID.randomUUID(),
                f.actual, 4, Actionable.MODULATE));
        assertEquals(4, pending(f));
        assertEquals(4, f.job.waitingFor.list.get(f.expected));
    }

    @Test void directedReturnsAlsoUseOverloadAccountingAndRejectOtherJobs() {
        var f = fixture(false, 4);
        f.register(4);
        assertEquals(0, f.logic.insertForJob(UUID.randomUUID(), f.actual, 4, Actionable.MODULATE));
        assertEquals(4, f.logic.insertForJob(f.job.link.getCraftingID(), f.actual, 4, Actionable.MODULATE));
        assertEquals(0, pending(f));
        assertEquals(4, f.logic.getInventory().list.get(f.actual));
    }

    @Test void finalVariantCompletesTheJobAndClearsTheAttachment() {
        var f = fixture(true, 4);
        f.register(4);
        when(f.job.link.insert(f.actual, 4, Actionable.MODULATE)).thenReturn(4L);
        assertEquals(4, f.logic.insert(f.actual, 4, Actionable.MODULATE));
        assertNull(f.logic.getJob());
        assertEquals(0, pending(f));
        verify(f.job.link).markDone();
    }

    @Test void partialRequesterAcceptanceRetainsTheConcreteRemainderAndRetriesIt() {
        var f = fixture(true, 4);
        f.register(4);
        when(f.job.link.insert(eq(f.actual), anyLong(), any())).thenAnswer(call ->
                Math.min(2L, call.<Long>getArgument(1)));
        assertEquals(4, f.logic.insert(f.actual, 4, Actionable.MODULATE));
        assertEquals(0, pending(f));
        assertEquals(0, f.job.waitingFor.list.get(f.expected));
        assertEquals(2, f.job.remainingAmount);
        assertEquals(2, f.logic.getInventory().list.get(f.actual));
        assertNotNull(f.logic.getJob());
        f.logic.outputDeliveryForPersistence().deliverStoredFinalOutput();
        assertNull(f.logic.getJob());
        assertEquals(0, f.logic.getInventory().list.get(f.actual));
    }

    @Test void reentrantRequesterCannotReceiveTheSameClaimTwice() {
        var f = fixture(true, 4);
        f.register(4);
        when(f.job.link.insert(eq(f.actual), anyLong(), any())).thenAnswer(call -> {
            if (call.getArgument(2) == Actionable.MODULATE) {
                assertEquals(0, pending(f), "overload ownership retires before requester callbacks");
                assertEquals(0, f.logic.insert(f.actual, 4, Actionable.MODULATE));
            }
            return call.getArgument(1);
        });
        assertEquals(4, f.logic.insert(f.actual, 4, Actionable.MODULATE));
        assertNull(f.logic.getJob());
        verify(f.job.link, times(1)).insert(f.actual, 4, Actionable.MODULATE);
    }

    @Test void partialCpuClaimDoesNotConsumeTheUnacceptedTail() {
        var f = fixture(false, 7);
        f.register(7);
        f.job.waitingFor.clear();
        f.job.waitingFor.insert(f.expected, 2, Actionable.MODULATE);
        assertEquals(2, ECOOverloadCpuAccountingBridge.claim(f.logic, f.job.link.getCraftingID(),
                f.actual, 7, Actionable.MODULATE));
        assertEquals(5, pending(f));
    }

    @Test void exactAndIdOnlyPatternsWithTheSameItemAreSerializedBeforeInputsMove() {
        var f = fixture(false, 4);
        f.job.waitingFor.insert(f.actual, 1, Actionable.MODULATE);
        assertFalse(ECOOverloadCpuAccountingBridge.prepare(f.logic, f.pattern,
                f.job.link.getCraftingID(), null, f.job.waitingFor.list).canDispatch());
        f.job.waitingFor.clear();
        f.register(4);
        var ordinary = mock(IPatternDetails.class);
        when(ordinary.getOutputs()).thenReturn(List.of(new GenericStack(f.actual, 1)));
        when(ordinary.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        assertFalse(ECOOverloadCpuAccountingBridge.prepare(f.logic, ordinary,
                f.job.link.getCraftingID(), null, f.job.waitingFor.list).canDispatch());
    }

    @Test void attachmentRoundTripRestoresRemainingClaimsAndCancellationClearsThem() throws Exception {
        var f = fixture(false, 7);
        f.register(7);
        assertEquals(2, f.logic.insert(f.actual, 2, Actionable.MODULATE));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        var original = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missing = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(missing.get()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missing);
        try (var keys = mockStatic(AEKeyTypesInternal.class);
             var types = mockStatic(AEKeyTypes.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            types.when(() -> AEKeyTypes.get(any(ResourceLocation.class)))
                    .thenAnswer(call -> LargeWorkstationTestKeys.REGISTRY.get(call.<ResourceLocation>getArgument(0)));
            var checkpoint = new CompoundTag();
            f.logic.writeJobAttachments(checkpoint, registries);
            assertTrue(checkpoint.getCompound("attachments").contains("ae2lt:overload_outputs"));
            ECOOverloadCpuAccountingBridge.clear(f.logic);
            assertEquals(0, pending(f));
            f.logic.loadJobAttachments(checkpoint, registries);
            assertEquals(5, pending(f));
            assertEquals(3, f.logic.insert(f.actual, 3, Actionable.MODULATE));
            assertEquals(2, pending(f));
            f.logic.cancel();
            assertNull(f.logic.getJob());
            assertEquals(0, pending(f));
        } finally {
            itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, original);
        }
    }

    @Test void loadingAnEmptyCpuCheckpointDiscardsOldLiveClaims() {
        var f = fixture(false, 4);
        f.register(4);
        f.logic.readFromNBT(new CompoundTag(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertNull(f.logic.getJob());
        assertEquals(0, pending(f));
    }

    private static long pending(Fixture f) {
        return OverloadCpuStateManager.INSTANCE.getRemainingForItem(f.logic, f.expected.getId());
    }

    private static Fixture fixture(boolean finalOutput, long amount) {
        var template = new ItemStack(Items.DIAMOND_PICKAXE);
        template.set(DataComponents.CUSTOM_NAME, Component.literal("expected"));
        var actualStack = template.copy();
        actualStack.set(DataComponents.CUSTOM_NAME, Component.literal("actual"));
        var expected = AEItemKey.of(template);
        var actual = AEItemKey.of(actualStack);
        var source = mock(IPatternDetails.class);
        when(source.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        when(source.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(source.getOutputs()).thenReturn(List.of(new GenericStack(expected, 1)));
        var parsed = new ParsedPatternDefinition(new SourcePatternSnapshot(
                ResourceLocation.parse("test:overload"), null, null), List.of(),
                List.of(new ParsedPatternOutput(0, template, true)));
        var details = new OverloadPatternDetails(parsed, EncodedOverloadPattern.builder()
                .output(0, MatchMode.ID_ONLY).build());
        var pattern = new Ae2OverloadPatternDetails(source.getDefinition(), details, source);
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        when(cpu.getOutputClaimSink()).thenReturn(logic);
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        var plan = mock(ICraftingPlan.class);
        when(plan.finalOutput()).thenReturn(new GenericStack(finalOutput ? expected : AEItemKey.of(Items.DIAMOND), amount));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(Map.of());
        ExecutingCraftingJob job;
        try (var trackers = mockConstruction(ElapsedTimeTracker.class)) {
            job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
        }
        logic.setJobFromPersistence(job);
        logic.jobAttachmentsForLifecycle().initialize(logic.createJobContext(job));
        return new Fixture(logic, job, pattern, expected, actual);
    }

    private record Fixture(ECOCraftingCPULogic logic, ExecutingCraftingJob job,
            IPatternDetails pattern, AEItemKey expected, AEItemKey actual) {
        void register(long count) {
            var registration = ECOOverloadCpuAccountingBridge.prepare(logic, pattern,
                    job.link.getCraftingID(), job.finalOutput.what(), job.waitingFor.list);
            assertTrue(registration.canDispatch());
            registration.registerAccepted(count);
            job.waitingFor.insert(expected, count, Actionable.MODULATE);
        }
    }
}
