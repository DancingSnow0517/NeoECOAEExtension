package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.EAEPForcedCrafting;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForcedCraftingSubmissionTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static AEKey key() {
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        when(key.getPrimaryKey()).thenReturn(new Object());
        return key;
    }

    private static ICraftingPlan plan(AEKey key) {
        var missing = new KeyCounter(); missing.add(key, 7);
        var emitted = new KeyCounter(); emitted.add(key, 2);
        return new CraftingPlan(new GenericStack(key, 1), 64, true, false,
            new KeyCounter(), emitted, missing, Map.of());
    }

    @Test void fallbackPreservesMissingInputsWithoutMutatingTheSimulation() {
        var key = key();
        var original = plan(key);
        try (var mods = mockStatic(ModList.class)) {
            var forced = EAEPForcedCrafting.force(original);
            assertInstanceOf(ECOForcedCraftingPlan.class, forced);
            assertFalse(forced.simulation());
            assertTrue(forced.missingItems().isEmpty());
            assertEquals(9, forced.emittedItems().get(key));
            forced.emittedItems().clear();
            assertEquals(9, forced.emittedItems().get(key));
            assertTrue(original.simulation());
            assertEquals(7, original.missingItems().get(key));
            assertEquals(2, original.emittedItems().get(key));
        }
    }

    @Test void fallbackCpuAcceptsPartialRefillsAndPersistsRemainingDemand() {
        var key = key();
        try (var mods = mockStatic(ModList.class); var types = mockStatic(AEKeyTypes.class)) {
            verifyWaitingLifecycle(EAEPForcedCrafting.force(plan(key)), key);
        }
    }

    @Test void overflowCannotTurnMissingInputsIntoANegativeWaitingAmount() {
        var key = key();
        var original = plan(key);
        original.emittedItems().set(key, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> new ECOForcedCraftingPlan(original));
        assertEquals(7, original.missingItems().get(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"appeng.crafting.execution.ExecutingCraftingJob",
        "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob"})
    void fallbackAlsoRegistersMissingInputsInVanillaAndQuantumJobs(String className) throws Exception {
        var key = key();
        try (var types = mockStatic(AEKeyTypes.class)) {
            var type = Class.forName(className);
            var listener = java.util.Arrays.stream(type.getDeclaredClasses())
                .filter(nested -> nested.getSimpleName().equals("CraftingDifferenceListener")).findFirst().orElseThrow();
            var constructor = type.getDeclaredConstructor(ICraftingPlan.class, listener, CraftingLink.class, Integer.class);
            constructor.setAccessible(true);
            var job = constructor.newInstance(new ECOForcedCraftingPlan(plan(key)), mock(listener),
                mock(CraftingLink.class), null);
            var waiting = type.getDeclaredField("waitingFor");
            waiting.setAccessible(true);
            assertEquals(9, ((appeng.crafting.inv.ListCraftingInventory) waiting.get(job)).list.get(key));
        }
    }

    static boolean eaepPresent() {
        try {
            Class.forName("com.extendedae_plus.crafting.ForcedCraftingPlan");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    @Test @EnabledIf("eaepPresent")
    void installedEaepTakesPriorityAndItsMissingInputsReachTheEcoCpu() {
        var key = key();
        try (var mods = mockStatic(ModList.class); var types = mockStatic(AEKeyTypes.class)) {
            var modList = mock(ModList.class);
            mods.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("extendedae_plus")).thenReturn(true);
            var forced = EAEPForcedCrafting.force(plan(key));
            assertEquals("com.extendedae_plus.crafting.ForcedCraftingPlan", forced.getClass().getName());
            assertTrue(EAEPForcedCrafting.isForced(forced));
            assertSame(forced, EAEPForcedCrafting.force(forced));
            assertEquals(2, forced.emittedItems().get(key));
            assertEquals(7, EAEPForcedCrafting.manualMissing(forced).get(key));
            verifyWaitingLifecycle(forced, key);
        }
    }

    private static void verifyWaitingLifecycle(ICraftingPlan forced, AEKey key) {
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        var link = mock(CraftingLink.class);
        when(link.getCraftingID()).thenReturn(UUID.randomUUID());
        var job = new ExecutingCraftingJob(forced, ignored -> {}, link, null);
        logic.setJobFromLifecycle(job);
        assertEquals(9, logic.getWaitingFor(key));
        assertEquals(4, logic.insert(key, 4, Actionable.SIMULATE));
        assertEquals(9, logic.getWaitingFor(key));
        assertTrue(logic.getInventory().list.isEmpty());
        assertEquals(4, logic.insert(key, 4, Actionable.MODULATE));
        assertEquals(5, logic.getWaitingFor(key));
        assertEquals(4, logic.getInventory().list.get(key));

        var registries = mock(HolderLookup.Provider.class);
        when(key.toTagGeneric(registries)).thenAnswer(call -> new CompoundTag());
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(eq(registries), any())).thenReturn(key);
            var saved = job.waitingFor.writeToNBT(registries);
            job.waitingFor.clear();
            job.waitingFor.readFromNBT(saved, registries);
        }
        assertEquals(5, logic.getWaitingFor(key));
        assertEquals(5, logic.insert(key, 100, Actionable.MODULATE));
        assertEquals(0, logic.getWaitingFor(key));
        assertEquals(9, logic.getInventory().list.get(key));
        assertEquals(0, logic.insert(key, 1, Actionable.MODULATE));
        logic.setJobFromLifecycle(null);
        assertEquals(0, logic.insert(key, 1, Actionable.MODULATE));
    }
}
