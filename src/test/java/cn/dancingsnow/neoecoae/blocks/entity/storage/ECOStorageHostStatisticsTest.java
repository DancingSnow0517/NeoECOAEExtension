package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.stacks.AEKeyType;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.impl.storage.StorageFaults;
import cn.dancingsnow.neoecoae.integration.biggerae2.BiggerAE2SingularityPowerService;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOStorageHostStatisticsTest {
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOTier.class.getName());
        }
    }

    @BeforeEach
    void registerKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
        keyTypes.when(AEKeyTypes::getAll).thenReturn(new java.util.LinkedHashSet<>(
            List.of(AEKeyType.items(), AEKeyType.fluids())));
    }

    @AfterEach
    void closeKeyTypes() {
        keyTypes.close();
    }

    @Test
    void energyDisplayIncludesTheHostsNetworkSingularitySurcharge() {
        Fixture fixture = new Fixture();
        long baseline = fixture.statistics.getEnergyConsumePerTick();
        var drive = fixture.addCell("items", AEKeyType.items(), 0, 5, 0, Long.MAX_VALUE);
        when(drive.getCellInventory().getIdleDrain()).thenReturn(1_024d);
        var node = mock(IManagedGridNode.class);
        var grid = mock(IGrid.class);
        var power = mock(BiggerAE2SingularityPowerService.class);
        when(fixture.host.getMainNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getService(BiggerAE2SingularityPowerService.class)).thenReturn(power);
        when(power.getAdditionalIdleDrain(fixture.host)).thenReturn(3_072d);
        try (var singularity = mockStatic(BiggerAE2SingularityPowerService.class)) {
            singularity.when(() -> BiggerAE2SingularityPowerService.isSingularity(drive.getCellStack())).thenReturn(true);
            assertEquals(baseline + 1_024 + 3_072, fixture.statistics.getEnergyConsumePerTick());
            verify(power).getAdditionalIdleDrain(fixture.host);
        }
        assertTrue(fixture.faults.snapshot().isEmpty());
    }

    @Test
    void cellsKeepPhysicalDrivePositionsAndPublishActualDriveStates() {
        Fixture fixture = new Fixture();
        ECODriveBlockEntity empty = mock(ECODriveBlockEntity.class);
        when(empty.getBlockPos()).thenReturn(new BlockPos(0, 1, 0));
        fixture.drives.add(empty);
        ECODriveBlockEntity first = fixture.addCell("items", AEKeyType.items(), 1, 10, 5, 100);
        ECODriveBlockEntity second = fixture.addCell("fluids", AEKeyType.fluids(), 2, 10, 90, 100);
        when(first.getCellInventory().getStatus()).thenReturn(CellState.TYPES_FULL);
        when(second.getCellInventory().getStatus()).thenReturn(CellState.FULL);
        when(second.isOnline()).thenReturn(false);
        UUID interactionId = UUID.randomUUID();
        when(first.getCellInteractionId()).thenReturn(interactionId);

        List<StorageHostUI.CellEntry> cells = fixture.statistics.getCellEntries();
        assertEquals(3, cells.size());
        assertFalse(cells.getFirst().installed());
        assertTrue(cells.getFirst().stack().isEmpty());
        assertEquals(empty.getBlockPos(), cells.getFirst().position());
        assertEquals(fixture.id("items"), cells.get(1).typeId());
        assertEquals(interactionId, cells.get(1).interactionId());
        assertEquals(fixture.id("fluids"), cells.get(2).typeId());
        assertEquals(CellState.TYPES_FULL, cells.get(1).state());
        assertEquals(CellState.FULL, cells.get(2).state());
        assertTrue(cells.get(1).online());
        assertFalse(cells.get(2).online());
        assertEquals(ResourceLocation.parse("neoecoae:items"), fixture.line("items").resourceId());

        when(first.getCellInventory().getUsedBytes()).thenReturn(99L);
        when(second.getCellInventory().getUsedBytes()).thenReturn(0L);
        when(fixture.host.getLevel().getGameTime()).thenReturn(25L);
        UUID replacementId = UUID.randomUUID();
        when(first.getCellInteractionId()).thenReturn(replacementId);
        fixture.statistics.invalidate();
        assertEquals(replacementId, fixture.statistics.getCellEntries().get(1).interactionId());
        assertEquals(List.of(-1, fixture.id("items"), fixture.id("fluids")),
            fixture.statistics.getCellEntries().stream().map(StorageHostUI.CellEntry::typeId).toList());
    }

    @Test
    void ordinaryAndMegaCellsShareTotalsButKeepIndividualCellCategories() {
        Fixture fixture = new Fixture();
        fixture.addCell("items", AEKeyType.items(), 2, 315, 100, 1_000);
        fixture.addCell("mega_item", AEKeyType.items(), 3, 50, 200, 2_000);
        fixture.addCell("fluids", AEKeyType.fluids(), 1, 25, 30, 300);
        fixture.addCell("mega_fluid", AEKeyType.fluids(), 4, 315, 70, 700);

        assertTotals(fixture.line("items"), 5, 365, 300, 3_000);
        assertTotals(fixture.line("fluids"), 5, 340, 100, 1_000);
        assertFalse(fixture.line("mega_item").visible().getAsBoolean());
        assertFalse(fixture.line("mega_fluid").visible().getAsBoolean());
        assertEquals("400", fixture.statistics.getTotalUsedBytesText());
        assertEquals(Set.of(fixture.id("items"), fixture.id("mega_item"),
            fixture.id("fluids"), fixture.id("mega_fluid")),
            fixture.statistics.getCellEntries().stream().map(StorageHostUI.CellEntry::typeId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(fixture.faults.snapshot().isEmpty());
    }

    @Test
    void emptyMegaCellPublishesCapacityUnderOrdinaryResourceGroup() {
        Fixture fixture = new Fixture();
        fixture.addCell("mega_item", AEKeyType.items(), 0, 315, 0, 4_096);

        assertTotals(fixture.line("items"), 0, 315, 0, 4_096);
        assertTrue(fixture.line("items").visible().getAsBoolean());
        assertFalse(fixture.line("mega_item").visible().getAsBoolean());
    }

    @ParameterizedTest
    @MethodSource("additionalResourceVariants")
    void variantCellsShareOrdinaryResourceNamesAndTotals(String variant, String ordinary) {
        Fixture fixture = new Fixture();
        fixture.registerType(ordinary);
        fixture.registerType(variant);
        fixture.addCell(ordinary, null, 2, 5, 100, 1_000);
        fixture.addCell(variant, null, 3, 7, 200, 2_000);

        StorageHostUI.StorageTypeLine line = fixture.line(ordinary);
        assertTotals(line, 5, 12, 300, 3_000);
        assertEquals(Component.literal(ordinary), line.displayName().get());
        assertEquals(ResourceLocation.fromNamespaceAndPath("neoecoae", ordinary), line.resourceId());
        assertTrue(line.visible().getAsBoolean());
        assertFalse(fixture.line(variant).visible().getAsBoolean());
        assertEquals(List.of(fixture.id(ordinary), fixture.id(variant)),
            fixture.statistics.getCellEntries().stream().map(StorageHostUI.CellEntry::typeId).toList());
        assertEquals("300", fixture.statistics.getTotalUsedBytesText());
        assertTrue(fixture.faults.snapshot().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("additionalResourceVariants")
    void emptyVariantCellPublishesCapacityUnderOrdinaryResourceGroup(String variant, String ordinary) {
        Fixture fixture = new Fixture();
        fixture.registerType(ordinary);
        fixture.registerType(variant);
        fixture.addCell(variant, null, 0, 5, 0, 4_096);

        assertTotals(fixture.line(ordinary), 0, 5, 0, 4_096);
        assertTrue(fixture.line(ordinary).visible().getAsBoolean());
        assertFalse(fixture.line(variant).visible().getAsBoolean());
        assertEquals(fixture.id(variant), fixture.statistics.getCellEntries().getFirst().typeId());
        assertTrue(fixture.faults.snapshot().isEmpty());
    }

    private static Stream<Arguments> additionalResourceVariants() {
        return Stream.of(
            Arguments.of("bigger_ae2_item", "items"),
            Arguments.of("bigger_ae2_fluid", "fluids"),
            Arguments.of("bigger_ae2_chemical", "mekanism"),
            Arguments.of("bigger_ae2_fe", "flux"),
            Arguments.of("bigger_ae2_experience", "experience"),
            Arguments.of("bigger_ae2_source", "source"),
            Arguments.of("mega_matter", "matter"),
            Arguments.of("mega_stress", "stress"),
            Arguments.of("mega_data", "data"));
    }

    @Test
    void megaChannelsMergeOnlyWhenTheirOrdinaryCategoryExists() {
        Fixture fixture = new Fixture();
        fixture.addCell("flux", null, 1, 1, 7, 70);
        fixture.addCell("mega_energy", null, 1, 1, 11, 110);
        fixture.addCell("mega_mana", null, 1, 1, 13, 130);
        fixture.addCell("bulk_item", AEKeyType.items(), 1, 1, 17, 170);
        fixture.addCell("omni", AEKeyType.items(), 1, 10, 19, 190);

        assertTotals(fixture.line("flux"), 2, 2, 18, 180);
        assertFalse(fixture.line("mega_energy").visible().getAsBoolean());
        assertTotals(fixture.line("mega_mana"), 1, 1, 13, 130);
        assertTrue(fixture.line("mega_mana").visible().getAsBoolean());
        assertTotals(fixture.line("bulk_item"), 1, 1, 17, 170);
        assertTotals(fixture.line("omni"), 1, 10, 19, 190);
        assertTotals(fixture.line("items"), 0, 0, 0, 0);
    }

    @Test
    void mergedCapacityRetainsUnboundedTypeLimitAndSaturatesByteTotals() {
        Fixture fixture = new Fixture();
        fixture.addCell("items", AEKeyType.items(), 2, -1, 1, Long.MAX_VALUE);
        fixture.addCell("mega_item", AEKeyType.items(), 3, 315, 2, 4_096);

        assertTotals(fixture.line("items"), 5, -1, 3, Long.MAX_VALUE);
    }

    @Test
    void infiniteDomainStillGroupsByKeyTypeAndDoesNotCountMemberCellsTwice() {
        Fixture fixture = new Fixture();
        ECODriveBlockEntity ordinary = fixture.addCell("items", AEKeyType.items(), 2, 315, 100, 1_000);
        ECODriveBlockEntity mega = fixture.addCell("mega_item", AEKeyType.items(), 3, 315, 200, 2_000);
        when(fixture.host.isInfiniteMemberCell(ordinary.getCellStack())).thenReturn(true);
        when(fixture.host.isInfiniteMemberCell(mega.getCellStack())).thenReturn(true);
        when(fixture.host.isFormedInfiniteMode()).thenReturn(true);
        ECOInfiniteStorageEngine engine = mock(ECOInfiniteStorageEngine.class);
        when(engine.getTypeStats()).thenReturn(List.of(
            new ECOInfiniteStorageEngine.TypeStats(AEKeyType.items(), 4, HugeAmount.of(80))));
        when(fixture.host.getInfiniteEngine()).thenReturn(engine);

        assertFalse(fixture.line("items").visible().getAsBoolean());
        assertFalse(fixture.line("mega_item").visible().getAsBoolean());
        StorageHostUI.StorageTypeLine infinite = fixture.lines().stream()
            .filter(line -> line.registryIndex() < 0 && line.visible().getAsBoolean())
            .filter(line -> line.usedTypes().getAsLong() > 0).findFirst().orElseThrow();
        assertEquals(4, infinite.usedTypes().getAsLong());
        assertEquals(0, infinite.totalTypes().getAsLong());
        assertTrue(fixture.statistics.getCellEntries().stream().allMatch(StorageHostUI.CellEntry::infinite));
        assertTrue(fixture.faults.snapshot().isEmpty());
    }

    private static void assertTotals(StorageHostUI.StorageTypeLine line,
        long usedTypes, long totalTypes, long usedBytes, long totalBytes) {
        assertEquals(usedTypes, line.usedTypes().getAsLong());
        assertEquals(totalTypes, line.totalTypes().getAsLong());
        assertEquals(usedBytes, line.usedBytes().getAsLong());
        assertEquals(totalBytes, line.totalBytes().getAsLong());
    }

    private static final class Fixture {
        final Registry<ECOCellType> cellTypes = new MappedRegistry<>(ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath("neoecoae_test", "storage_cell_types")), Lifecycle.stable());
        final ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        final NEStorageCluster cluster = mock(NEStorageCluster.class);
        final List<ECODriveBlockEntity> drives = new ArrayList<>();
        final StorageFaults faults = new StorageFaults();
        final ECOStorageHostStatistics statistics = new ECOStorageHostStatistics(host, cellTypes);

        Fixture() {
            for (String path : List.of("items", "fluids", "mega_item", "mega_fluid", "flux", "mega_energy",
                "mega_mana", "bulk_item", "omni")) {
                registerType(path);
            }
            when(host.getCluster()).thenReturn(cluster);
            when(host.getTier()).thenReturn(ECOTier.L9);
            Level level = mock(Level.class);
            when(host.getLevel()).thenReturn(level);
            when(host.storageFaults()).thenReturn(faults);
            when(cluster.getDrives()).thenReturn(drives);
            when(host.getStorageUiDrives()).thenReturn(drives);
            when(cluster.getEnergyCells()).thenReturn(List.of());
        }

        void registerType(String path) {
            if (!cellTypes.containsKey(key(path))) {
                Registry.register(cellTypes, key(path), new ECOCellType(Component.literal(path), 1));
            }
        }

        ECODriveBlockEntity addCell(String path, AEKeyType keyType,
            long usedTypes, long totalTypes, long usedBytes, long totalBytes) {
            ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
            IECOStorageCell inventory = mock(IECOStorageCell.class);
            ItemStack stack = mock(ItemStack.class);
            Item item = mock(Item.class, withSettings().extraInterfaces(IECOStorageCellItem.class));
            when(((IECOStorageCellItem) item).getKeyTypes()).thenReturn(keyType == null ? Set.of() : Set.of(keyType));
            when(stack.getItem()).thenReturn(item);
            when(stack.copy()).thenReturn(stack);
            when(drive.getCellStack()).thenReturn(stack);
            when(drive.getCellInventory()).thenReturn(inventory);
            when(drive.getBlockPos()).thenReturn(new BlockPos(drives.size(), 0, 0));
            when(inventory.getCellType()).thenReturn(cellTypes.get(key(path)));
            when(inventory.getTier()).thenReturn(ECOTier.L9);
            when(inventory.getStoredItemTypes()).thenReturn(usedTypes);
            when(inventory.getTotalItemTypes()).thenReturn(totalTypes);
            when(inventory.hasInfiniteTypeCapacity()).thenReturn(totalTypes < 0);
            when(inventory.getUsedBytes()).thenReturn(usedBytes);
            when(inventory.getTotalBytes()).thenReturn(totalBytes);
            when(inventory.getStatus()).thenReturn(CellState.NOT_EMPTY);
            when(drive.isOnline()).thenReturn(true);
            drives.add(drive);
            statistics.invalidate();
            return drive;
        }

        List<StorageHostUI.StorageTypeLine> lines() {
            return statistics.createStorageTypeLines();
        }

        StorageHostUI.StorageTypeLine line(String path) {
            return lines().stream().filter(line -> line.registryIndex() == id(path)).findFirst().orElseThrow();
        }

        int id(String path) {
            return cellTypes.getId(cellTypes.get(key(path)));
        }

        private static ResourceLocation key(String path) {
            return ResourceLocation.fromNamespaceAndPath("neoecoae", path);
        }
    }
}
