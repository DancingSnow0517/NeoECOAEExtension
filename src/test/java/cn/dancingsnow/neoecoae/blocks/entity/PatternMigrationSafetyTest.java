package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertion;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.PatternStorageHost;
import cn.dancingsnow.neoecoae.grid.PatternCatalog;
import cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternMigrationSafetyTest {
    abstract static class Host implements PatternStorageHost, PatternContainer, IECOPatternStorage {}

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(ints = {72, 73})
    void migrating72UniquePatternsToTheirOwnDomainPreservesEveryPatternAndReturnsNoBlanks(int slots)
            throws Exception {
        var f = new Fixture();
        var source = f.host(slots);
        for (int i = 0; i < 72; i++) source.items[i] = pattern(i);
        f.register(source);
        try (var patterns = f.patterns()) {
            f.finishIndex();
            var machine = f.machine(source.host);
            f.tick(machine);

            assertNull(field(machine, "patternTransferTask"));
            assertEquals(72, Arrays.stream(source.items).filter(stack -> !stack.isEmpty()).count());
            for (int i = 0; i < 72; i++) {
                assertTrue(ItemStack.isSameItemSameComponents(pattern(i), source.items[i]));
            }
            assertEquals(0, field(machine, "patternTransferInserted"));
            assertEquals(0, field(machine, "patternTransferAlreadyPresent"));
            assertEquals(72, field(machine, "patternTransferUnchanged"));
            verify(f.grid, never()).getStorageService();
            verify(source.inventory, never()).setItemDirect(anyInt(), any());
            assertEquals(72, f.catalog.claimExternalPatternCandidates(f.grid, UUID.randomUUID(), 100)
                    .candidates().size());
        }
    }

    @Test void crossDomainMovePreservesTheEncodedItemWithoutMintingABlank() throws Exception {
        var f = new Fixture();
        var source = f.host(73);
        source.items[64] = pattern(64);
        var target = f.host(2);
        f.register(source);
        f.register(target);
        try (var patterns = f.patterns()) {
            f.finishIndex();
            var machine = f.machine(target.host);
            f.tick(machine);

            assertTrue(source.items[64].isEmpty());
            assertTrue(ItemStack.isSameItemSameComponents(pattern(64), target.items[0]));
            assertEquals(1, field(machine, "patternTransferInserted"));
            assertEquals(0, field(machine, "patternTransferAlreadyPresent"));
            verify(f.grid, never()).getStorageService();
        }
    }

    @Test void anotherPhysicalSlotStillCountsAsADuplicate() {
        var f = new Fixture();
        var source = f.host(3);
        source.items[0] = pattern(0);
        source.items[1] = pattern(0);
        f.register(source);

        assertEquals(ECOPatternInsertionResult.ALREADY_PRESENT,
                f.route(source, source.host).result());
        verify(source.host, never()).insertPreparedPatternKnownUnique(any());
    }

    @Test void auxiliaryCopyStillCountsAsADuplicateWhenThePhysicalSourceIsExcluded() {
        var f = new Fixture();
        var source = f.host(2);
        source.items[0] = pattern(0);
        when(source.host.getAuxiliaryEncodedPatterns()).thenReturn(List.of(pattern(0)));
        f.register(source);

        assertEquals(ECOPatternInsertionResult.ALREADY_PRESENT,
                f.route(source, source.host).result());
    }

    @Test void duplicateInAnotherDomainDoesNotBlockAUniqueDestination() {
        var f = new Fixture();
        var source = f.host(2);
        source.items[0] = pattern(0);
        var unrelated = f.host(2);
        unrelated.items[0] = pattern(0);
        var target = f.host(2);
        f.register(source);
        f.register(unrelated);
        f.register(target);

        assertEquals(ECOPatternInsertionResult.INSERTED, f.route(source, target.host).result());
        assertTrue(ItemStack.isSameItemSameComponents(pattern(0), target.items[0]));
    }

    @ParameterizedTest
    @EnumSource(value = ECOPatternInsertionResult.class, names = {"INSERTED", "ALREADY_PRESENT"})
    void sourceChangesReleaseTheClaimWithoutClearingOrRefundingTheSource(ECOPatternInsertionResult result)
            throws Exception {
        var f = new Fixture();
        var source = f.host(2);
        source.items[1] = pattern(1);
        f.register(source);
        try (var patterns = f.patterns()) {
            f.finishIndex();
            var candidate = new ECOPatternSourceSlot(source.host, 1);
            doAnswer(call -> {
                source.visibleSlots.set(1);
                return ECOPatternInsertion.of(result);
            }).when(f.catalog).insertPreparedPatternReporting(any(), any(), eq(candidate));
            var machine = f.machine(source.host);
            f.tick(machine);

            assertFalse(source.items[1].isEmpty());
            assertEquals(1, field(machine, "patternTransferSourceChanged"));
            verify(f.grid, never()).getStorageService();
            verify(f.catalog).releaseExternalPatternCandidate(candidate);
            verify(f.catalog, never()).removeExternalPatternCandidate(candidate);
            source.visibleSlots.set(2);
            assertEquals(List.of(candidate),
                    f.catalog.claimExternalPatternCandidates(f.grid, UUID.randomUUID(), 64).candidates());
        }
    }

    @Test void aPageShrinkingBeforeTheCandidateIsReadReleasesItForALaterPass() throws Exception {
        var f = new Fixture();
        var source = f.host(73);
        source.items[64] = pattern(64);
        f.register(source);
        try (var patterns = f.patterns()) {
            f.finishIndex();
            var machine = f.machine(source.host);
            source.visibleSlots.set(63);
            f.tick(machine);

            var candidate = new ECOPatternSourceSlot(source.host, 64);
            assertFalse(source.items[64].isEmpty());
            verify(f.catalog).releaseExternalPatternCandidate(candidate);
            verify(f.catalog, never()).removeExternalPatternCandidate(candidate);
            source.visibleSlots.set(73);
            assertEquals(List.of(candidate),
                    f.catalog.claimExternalPatternCandidates(f.grid, UUID.randomUUID(), 64).candidates());
        }
    }

    @Test void excludedCandidatesStayClaimableByOtherOwnersWithoutHidingTheNextBatch() {
        var f = new Fixture();
        var source = f.host(73);
        for (int i = 0; i < 73; i++) source.items[i] = pattern(i);
        f.register(source);
        try (var patterns = f.patterns()) {
            f.finishIndex();
            var owner = UUID.randomUUID();
            var first = f.catalog.claimExternalPatternCandidates(f.grid, owner, 64).candidates();
            first.forEach(f.catalog::releaseExternalPatternCandidate);
            var next = f.catalog.claimExternalPatternCandidates(f.grid, owner, 64, Set.copyOf(first));
            assertEquals(9, next.candidates().size());
            assertTrue(f.catalog.claimExternalPatternCandidates(f.grid, owner, 64, Set.copyOf(first))
                    .candidates().isEmpty());
            assertEquals(64, f.catalog.claimExternalPatternCandidates(f.grid, UUID.randomUUID(), 64)
                    .candidates().size());
        }
    }

    private record HostSlots(Host host, InternalInventory inventory, ItemStack[] items,
                             AtomicInteger visibleSlots) {}

    private static class Fixture {
        final IGrid grid = mock(IGrid.class);
        final PatternCatalog catalog = spy(new PatternCatalog());
        final IPatternDetails details = mock(IPatternDetails.class,
                withSettings().extraInterfaces(IMolecularAssemblerSupportedPattern.class));
        final java.util.Set<Host> hosts = new java.util.LinkedHashSet<>();

        Fixture() {
            when(grid.getMachineClasses()).thenReturn(List.of(Host.class));
            when(grid.getActiveMachines(Host.class)).thenReturn(hosts);
        }

        HostSlots host(int slots) {
            var host = mock(Host.class);
            var inventory = mock(InternalInventory.class);
            var items = new ItemStack[slots];
            Arrays.fill(items, ItemStack.EMPTY);
            var visible = new AtomicInteger(slots);
            var revision = new AtomicInteger();
            when(host.getGrid()).thenReturn(grid);
            when(host.allowsPatternMigrationSource()).thenReturn(true);
            when(host.getPatternSlotInventory()).thenReturn(inventory);
            when(host.getPatternSlotCount()).thenAnswer(call -> visible.get());
            when(host.getPatternContentRevision()).thenAnswer(call -> revision.get());
            when(host.shouldIndexPattern(any())).thenReturn(true);
            when(host.getAuxiliaryEncodedPatterns()).thenReturn(List.of());
            when(host.getBlockPos()).thenReturn(new BlockPos(hosts.size(), 0, 0));
            when(inventory.size()).thenAnswer(call -> visible.get());
            when(inventory.getStackInSlot(anyInt())).thenAnswer(call -> {
                int slot = call.getArgument(0);
                return slot >= 0 && slot < visible.get() ? items[slot] : ItemStack.EMPTY;
            });
            doAnswer(call -> {
                items[call.getArgument(0, Integer.class)] = call.getArgument(1);
                revision.incrementAndGet();
                return null;
            }).when(inventory).setItemDirect(anyInt(), any());
            when(host.insertPreparedPatternKnownUnique(any())).thenAnswer(call -> {
                var prepared = call.getArgument(0, ECOPreparedPattern.class);
                for (int i = 0; i < visible.get(); i++) {
                    if (items[i].isEmpty()) {
                        inventory.setItemDirect(i, prepared.stack().copy());
                        return ECOPatternInsertionResult.INSERTED;
                    }
                }
                return ECOPatternInsertionResult.NO_SPACE;
            });
            hosts.add(host);
            return new HostSlots(host, inventory, items, visible);
        }

        void register(HostSlots slots) {
            var node = mock(IGridNode.class);
            when(node.getService(IECOPatternStorage.class)).thenReturn(slots.host);
            catalog.addNode(node, null);
        }

        org.mockito.MockedStatic<PatternDetailsHelper> patterns() {
            var patterns = mockStatic(PatternDetailsHelper.class);
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                    .thenAnswer(call -> call.getArgument(0, ItemStack.class).has(AEComponents.ENCODED_CRAFTING_PATTERN));
            patterns.when(() -> PatternDetailsHelper.decodePattern(any(ItemStack.class), any())).thenReturn(details);
            return patterns;
        }

        ECOPatternInsertion route(HostSlots source, Object domain) {
            var stack = source.items[0];
            return catalog.insertPreparedPatternReporting(domain,
                    new ECOPreparedPattern(stack, details, AEItemKey.of(stack)),
                    new ECOPatternSourceSlot(source.host, 0));
        }

        void finishIndex() {
            for (int i = 0; i < 1000; i++) {
                if (catalog.getExternalPatternIndex(grid).ready()) return;
                catalog.onServerEndTick();
            }
            fail("Migration source index did not finish");
        }

        ECOMachineInterfaceBlockEntity<?> machine(Object domain) throws Exception {
            var machine = mock(ECOMachineInterfaceBlockEntity.class, CALLS_REAL_METHODS);
            var node = mock(IManagedGridNode.class);
            doReturn(node).when(machine).getMainNode();
            when(node.getGrid()).thenReturn(grid);
            field(machine, "formed", true);
            field(machine, "level", mock(ServerLevel.class));
            Class<?> taskClass = Arrays.stream(ECOMachineInterfaceBlockEntity.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("PatternTransferTask")).findFirst().orElseThrow();
            var constructor = taskClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var task = constructor.newInstance(machine, grid, catalog, catalog, domain);
            field(machine, "patternTransferTask", task);
            assertTrue(PatternMigrationCoordinator.forGrid(grid).tryAcquire(machine));
            return machine;
        }

        void tick(ECOMachineInterfaceBlockEntity<?> machine) throws Exception {
            var method = ECOMachineInterfaceBlockEntity.class.getDeclaredMethod(
                    "tickPatternTransfer", ServerLevel.class, long.class);
            method.setAccessible(true);
            method.invoke(machine, field(machine, "level"), Long.MAX_VALUE);
        }
    }

    private static Field findField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        return findField(target, name).get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        findField(target, name).set(target, value);
    }

    private static ItemStack pattern(int index) {
        var stack = new ItemStack(Items.STONE);
        stack.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(List.of(),
                new ItemStack(Items.DIAMOND), ResourceLocation.withDefaultNamespace("test_" + index), false, false));
        return stack;
    }
}
