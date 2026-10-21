package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI.CellEntry;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import appeng.api.storage.cells.CellState;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoragePanelStateTest {
    @Test
    void addonTierNameSurvivesPagingAndTransportWithoutBeingInferredFromItsNumber() {
        var addon = org.mockito.Mockito.mock(cn.dancingsnow.neoecoae.api.IECOTier.class);
        Component name = Component.translatable("prototype.tier.l1").withStyle(style -> style.withColor(0x12AB34));
        org.mockito.Mockito.when(addon.getPanelTierName()).thenReturn(name);
        CellEntry entry = new CellEntry(0, 0, CellEntry.KIND_ITEM, 0, 10, 0, 100,
            false, CellState.EMPTY, true).withPanelTier(addon)
            .atDrive(new BlockPos(1, 2, 3), new ItemStack(Items.DIAMOND), null);
        StoragePanelState received = StoragePanelState.fromTag(
            StoragePanelState.capture(config(List.of(entry), false), 0, 33).toTag());
        assertTrue(cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells()).installed());
        assertEquals(name, StorageCellGrid.tierName(cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells())));
        assertEquals(0, cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells()).tier());
        assertEquals(Component.literal("L4"), StorageCellGrid.tierName(cell(0, 100)));
    }

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void onlyTheRequestedPageIsSerializedAndRemovedCellsClearStalePages() {
        List<CellEntry> cells = new ArrayList<>();
        for (int i = 0; i < 72; i++) cells.add(cell(i, 100));
        StorageHostUI.Config config = config(cells, false);
        StoragePanelState first = StoragePanelState.capture(config, 0, 33);
        assertEquals(72, first.cellCount());
        assertEquals(33, first.toTag().getList("cells", Tag.TAG_COMPOUND).size());
        assertEquals(BigInteger.valueOf(7_200), first.totalBytes());
        assertEquals(0, first.cells().get(0).usedBytes());
        assertEquals(24, first.cells().get(11).usedBytes());
        assertEquals(48, first.cells().get(22).usedBytes());
        StoragePanelState last = StoragePanelState.capture(config, Integer.MAX_VALUE, 33);
        assertEquals(66, last.cellOffset());
        assertEquals(first.totalBytes(), last.totalBytes());
        assertEquals(first.totalBytes(), StoragePanelState.fromTag(last.toTag()).totalBytes());
        assertEquals(List.of(22L, 23L, 46L, 47L, 70L, 71L),
            last.cells().stream().filter(CellEntry::installed).map(CellEntry::usedBytes).toList());
        cells.subList(6, cells.size()).clear();
        StoragePanelState shortened = StoragePanelState.capture(config, last.cellOffset(), 33);
        assertEquals(0, shortened.cellOffset());
        assertEquals(BigInteger.valueOf(600), shortened.totalBytes());
        assertEquals(33, shortened.cells().size());
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L),
            shortened.cells().stream().filter(CellEntry::installed).map(CellEntry::usedBytes).toList());
        assertFalse(shortened.cells().get(2).installed());
        assertEquals(2, shortened.cells().get(11).usedBytes());
        cells.clear();
        assertTrue(StoragePanelState.capture(config, 66, 33).cells().isEmpty());
    }

    @Test
    void diskComponentsPhysicalPositionAndExtractionLockTravelInOneSnapshot() {
        ItemStack disk = new ItemStack(Items.DIAMOND);
        CompoundTag data = new CompoundTag();
        data.putString("disk-id", "stored-resources-42");
        disk.setTag(data.copy());
        disk.setHoverName(Component.literal("My disk"));
        BlockPos position = new BlockPos(10, 70, 34);
        Component lock = Component.translatable("tooltip.neoecoae.storage.infinite_migration_locked");
        var interactionId = java.util.UUID.randomUUID();
        CellEntry entry = cell(50, 100).atDrive(position, disk, lock, interactionId);
        StoragePanelState received = StoragePanelState.fromTag(StoragePanelState.capture(config(List.of(entry), false), 0, 33).toTag());
        CellEntry transported = cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells());
        assertEquals(position, transported.position());
        assertEquals(interactionId, transported.interactionId());
        assertEquals(lock, transported.extractionBlockReason());
        assertTrue(ItemStack.matches(disk, transported.stack()));
        disk.setHoverName(Component.literal("Changed after capture"));
        assertEquals(Component.literal("My disk"), entry.stack().getHoverName());
    }

    @Test
    void emptyPhysicalDrivesKeepTheirPositionsWhilePaddingHasNoInstallationTarget() {
        BlockPos position = new BlockPos(5, 60, 14);
        CellEntry emptyDrive = CellEntry.EMPTY.atDrive(position, ItemStack.EMPTY, null);
        var received = StoragePanelState.fromTag(StoragePanelState.capture(config(List.of(emptyDrive), false), 0, 33).toTag());
        assertEquals(position, cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells()).position());
        assertFalse(cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells()).installed());
        assertTrue(cn.dancingsnow.neoecoae.platform.JdkCompat.first(received.cells()).stack().isEmpty());
        assertNull(received.cells().get(1).position());
    }

    @Test
    void combinedCapacityDoesNotOverflowALong() {
        long finiteCapacity = Long.MAX_VALUE - 1;
        List<CellEntry> cells = List.of(cell(finiteCapacity / 2, finiteCapacity),
            cell(finiteCapacity / 2, finiteCapacity), cell(finiteCapacity / 2, finiteCapacity));
        double ratio = StoragePanelState.usage(cells);
        assertEquals(0.5, ratio, 1.0E-14);
        StoragePanelState captured = StoragePanelState.capture(config(cells, false), 0, 33);
        assertEquals(BigInteger.valueOf(finiteCapacity).multiply(BigInteger.valueOf(3)), captured.totalBytes());
        assertEquals(captured.totalBytes(), StoragePanelState.fromTag(captured.toTag()).totalBytes());
        assertEquals(0, StoragePanelState.usage(List.of()));
        assertEquals(1, StoragePanelState.usage(List.of(cell(5, -1))));
    }

    @Test
    void specialCellCapacityIsExcludedFromHostTotalsAndUsageAcrossPages() {
        CellEntry special = cell(1_000, Long.MAX_VALUE);
        StoragePanelState onlySpecial = StoragePanelState.capture(config(List.of(special), false), 0, 33);
        assertEquals(BigInteger.ZERO, onlySpecial.totalBytes());
        assertEquals(0, onlySpecial.usage());
        assertFalse(onlySpecial.unbounded());
        assertEquals(special, cn.dancingsnow.neoecoae.platform.JdkCompat.first(onlySpecial.cells()));

        List<CellEntry> mixed = new ArrayList<>(java.util.Collections.nCopies(35, cell(50, 100)));
        mixed.add(special);
        for (int offset : List.of(0, 33)) {
            StoragePanelState captured = StoragePanelState.capture(config(mixed, false), offset, 33);
            assertEquals(BigInteger.valueOf(3_500), captured.totalBytes());
            assertEquals(0.5, captured.usage(), 1.0E-14);
            StoragePanelState synced = StoragePanelState.fromTag(captured.toTag());
            assertEquals(captured.totalBytes(), synced.totalBytes());
            assertEquals(captured.usage(), synced.usage());
        }
    }

    @Test
    void finiteCapacityShowsColoredUsedAndMaximumWhileInfiniteShowsOnlyPurpleUsage() {
        int[] used = {0, 90, 100};
        int[] usedColors = {HostText.USED, HostText.ORANGE, HostText.ERROR};
        for (int index = 0; index < used.length; index++) {
            StoragePanelState state = new StoragePanelState(Component.empty(), 2, true, false, false,
                0, 100, 0, Integer.toString(used[index]), used[index] / 100D, 1, 0,
                List.of(cell(used[index], 100)), List.of(), "", 0, false);
            Component caption = StorageHostCharts.capacityValue(state);
            assertEquals(used[index] + " / 100", caption.getString());
            List<Integer> colors = new ArrayList<>();
            caption.visit((style, text) -> {
                if (!text.isEmpty()) colors.add(style.getColor().getValue());
                return java.util.Optional.<Void>empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            assertEquals(List.of(usedColors[index], 0x3F3D52, StoragePanelTextures.VALUE_TEXT), colors);
        }
        Component infinite = StorageHostCharts.capacityValue(barState(true, 1, List.of()));
        assertEquals("150", infinite.getString());
        assertEquals(StoragePanelTextures.VALUE_TEXT, infinite.getStyle().getColor().getValue());
    }

    @Test
    void uninstalledTypesAreOmittedWhileAnInstalledEmptyDiskRetainsItsCard() {
        List<CellEntry> cells = new ArrayList<>();
        cells.add(cell(0, 0).atDrive(BlockPos.ZERO, new ItemStack(Items.DIAMOND), null));
        StorageHostUI.Config base = config(cells, false);
        var absent = new StorageHostUI.StorageTypeLine(new ECOCellType(Component.literal("unused"), 1), 8,
            () -> 0, () -> 0, () -> 0, () -> 0, () -> "0");
        var empty = new StorageHostUI.StorageTypeLine(new ECOCellType(Component.literal("installed"), 1), 7,
            () -> 0, () -> 0, () -> 0, () -> 0, () -> "0");
        StorageHostUI.Config config = new StorageHostUI.Config(base.title(), base.tier(), base.formed(),
            base.storedEnergy(), base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(),
            base.cellEntries(), List.of(absent, empty), base.infiniteStorage(), base.migratingToInfinite(),
            base.canExtractInfiniteComponents(), base.domainId(), base.missingMembers(), base.infiniteComponentInventory());
        StoragePanelState installed = StoragePanelState.capture(config, 0, 33);
        assertEquals(List.of(7), installed.types().stream().map(StoragePanelState.TypeLine::id).toList());
        cells.set(0, CellEntry.EMPTY.atDrive(BlockPos.ZERO, ItemStack.EMPTY, null));
        assertTrue(StoragePanelState.capture(config, 0, 33).types().isEmpty());
    }

    @Test
    void emptyPhysicalDriveDoesNotCreateTheFirstInfiniteResourceCard() {
        StorageHostUI.Config base = config(List.of(
            CellEntry.EMPTY.atDrive(BlockPos.ZERO, ItemStack.EMPTY, null)), true);
        var unusedExperience = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("experience"), 1), -1,
            () -> 0, () -> 0, () -> 0, () -> 0, () -> "0");
        var storedFluid = new StorageHostUI.StorageTypeLine(
            new ECOCellType(Component.literal("fluid"), 1), -2,
            () -> 1, () -> 0, () -> 50, () -> 0, () -> "50");
        StorageHostUI.Config config = new StorageHostUI.Config(base.title(), base.tier(), base.formed(),
            base.storedEnergy(), base.maxEnergy(), base.energyConsumePerTick(), base.totalUsedBytesText(),
            base.cellEntries(), List.of(unusedExperience, storedFluid), base.infiniteStorage(), base.migratingToInfinite(),
            base.canExtractInfiniteComponents(), base.domainId(), base.missingMembers(), base.infiniteComponentInventory());

        StoragePanelState state = StoragePanelState.capture(config, 0, 33);
        assertEquals(List.of(-2), state.types().stream().map(StoragePanelState.TypeLine::id).toList());
        assertEquals(List.of(-2), StoragePanelState.fromTag(state.toTag()).types().stream()
            .map(StoragePanelState.TypeLine::id).toList());
    }

    @Test
    void transportKeepsTranslatedNamesColorsAndHugeInfiniteAmounts() {
        String huge = "12345678901234567890123456789012345678901234567890";
        Component name = Component.translatable("custom.storage", Component.literal("sample").withStyle(style -> style.withColor(0x9BC9FE)))
            .withColor(0xFFB2D8);
        StoragePanelState.TypeLine type = new StoragePanelState.TypeLine(7, name, 0xFFB2D8,
            100, -1, Long.MAX_VALUE, -1, huge);
        StoragePanelState state = new StoragePanelState(Component.translatable("host.title"), 3, true, true, false,
            50, 100, 20, huge, 1, 100, 99, List.of(new CellEntry(7, 3, 1, 0, -1, 0, -1, true)),
            List.of(type), "test-domain", 2, true);
        assertEquals(state, StoragePanelState.fromTag(state.toTag()));
        assertEquals(huge, cn.dancingsnow.neoecoae.platform.JdkCompat.first(StoragePanelState.fromTag(state.toTag()).types()).bytesText());
        assertEquals(name, StoragePanelState.fromTag(state.toTag()).type(7).name());
    }

    @Test
    void resourceChannelsUseTheSuppliedLegendInNormalAndInfiniteModes() {
        String[] normal = {"mekanism", "fluids", "items", "air", "flux", "lightning",
            "experience", "soul", "source", "mana", "omni", "complex_omni", "quantum_omni", "spent_nuclear_waste", "data"};
        String[] infinite = {"appmek:chemical", "ae2:f", "ae2:i", "appliedpneumatics:air_type",
            "appflux:flux", "ae2lt:lightning", "appex:experience", "soulplied_energistics:soul", "arseng:source", "appbot:mana",
            "ae2omnicells:omni", "ae2omnicells:complex_omni", "ae2omnicells:quantum_omni", "ae2omnicells:spent_nuclear_waste_cell",
            "data_energistics:manifest_binary"};
        for (int i = 0; i < normal.length; i++) {
            assertEquals(i, StoragePanelTextures.swatchIndex(new ResourceLocation("neoecoae:" + normal[i])));
            assertEquals(i, StoragePanelTextures.swatchIndex(new ResourceLocation(infinite[i])));
        }
        assertEquals(2, StoragePanelTextures.swatchIndex(new ResourceLocation("neoecoae:bulk_item")));
        assertEquals(9, StoragePanelTextures.swatchIndex(new ResourceLocation("neoecoae:mega_mana")));
        assertEquals(13, StoragePanelTextures.swatchIndex(new ResourceLocation("mekanism:spent_nuclear_waste")));
        assertEquals(StoragePanelTextures.FALLBACK_SWATCH,
            StoragePanelTextures.swatchIndex(new ResourceLocation("other:mana")));
        assertEquals(14, StoragePanelTextures.swatchIndex(new ResourceLocation("data_energistics:digitalization")));
        var unknown = StoragePanelTextures.swatch(StoragePanelTextures.FALLBACK_SWATCH, 0x54E6F2);
        assertEquals(16, unknown.spritePosition.x);
        assertEquals(78, unknown.spritePosition.y);
        assertEquals(0xFF54E6F2, cn.dancingsnow.neoecoae.util.UiTestSupport.color(unknown));
        assertEquals(-1, cn.dancingsnow.neoecoae.util.UiTestSupport.color(StoragePanelTextures.swatch(7, 0xA56ACB)));
        for (int i = 10; i <= 13; i++) {
            int expectedX = 64 + (i - 10) * 8;
            var swatch = StoragePanelTextures.swatch(i, 0);
            assertEquals(expectedX, swatch.spritePosition.x);
            assertEquals(62, swatch.spritePosition.y);
            assertEquals(-1, cn.dancingsnow.neoecoae.util.UiTestSupport.color(swatch));

            var icon = StoragePanelTextures.resourceIcon(i, 0);
            assertEquals(expectedX, icon.spritePosition.x);
            assertEquals(86, icon.spritePosition.y);
        }
    }

    @Test
    void resourceDistributionMergesAliasesOrdersAmountsAndFillsTheBar() {
        List<StoragePanelState.TypeLine> types = List.of(type(1, 30), type(2, 40), type(0, 20), type(2, 60));
        StoragePanelState state = barState(false, 0.75, types);
        var segments = StorageHostCharts.capacitySegments(state, 160);
        assertEquals(List.of(2, 1, 0), segments.stream().map(StorageHostCharts.CapacitySegment::swatch).toList());
        assertEquals(List.of(107, 32, 21), segments.stream().map(StorageHostCharts.CapacitySegment::width).toList());
        assertEquals(160, segments.stream().mapToInt(StorageHostCharts.CapacitySegment::width).sum());
        assertTrue(StorageHostCharts.capacitySegments(barState(true, 1, List.of()), 160).isEmpty());
    }

    @Test
    void finiteResourceDistributionRemainsVisibleWhenCapacityUsageRoundsToZeroPixels() {
        var state = barState(false, 0.000001, List.of(type(2, 32_769), type(1, 81_796)));
        var transported = StoragePanelState.fromTag(state.toTag());
        assertEquals(0, Math.round(160 * transported.usage()));
        var segments = StorageHostCharts.capacitySegments(transported, 160);
        assertEquals(List.of(1, 2), segments.stream().map(StorageHostCharts.CapacitySegment::swatch).toList());
        assertEquals(List.of(114, 46), segments.stream().map(StorageHostCharts.CapacitySegment::width).toList());
        assertTrue(StorageHostCharts.capacitySegments(barState(false, 0.000001, List.of()), 160).isEmpty());
        assertTrue(StorageHostCharts.capacitySegments(transported, 0).isEmpty());
    }

    @Test
    void infiniteStackedBarUsesExactAmountsBeyondLongAndTransportsTheirProportions() {
        BigInteger huge = BigInteger.TEN.pow(100);
        StoragePanelState.TypeLine item = new StoragePanelState.TypeLine(2, Component.literal("item"), 0xEC8600,
            1, -1, Long.MAX_VALUE, -1, "3E100", 2, huge.multiply(BigInteger.valueOf(3)));
        StoragePanelState.TypeLine fluid = new StoragePanelState.TypeLine(1, Component.literal("fluid"), 0x3894FF,
            1, -1, Long.MAX_VALUE, -1, "1E100", 1, huge);
        StoragePanelState state = barState(true, 1, List.of(fluid, item));
        StoragePanelState transported = StoragePanelState.fromTag(state.toTag());
        assertEquals(state, transported);
        var segments = StorageHostCharts.capacitySegments(transported, 160);
        assertEquals(List.of(120, 40), segments.stream().map(StorageHostCharts.CapacitySegment::width).toList());
    }

    @Test
    void actualDriveStatesAndEmptyPositionsSurviveSynchronization() {
        List<CellEntry> cells = new ArrayList<>();
        cells.add(CellEntry.EMPTY);
        for (CellState state : CellState.values()) {
            cells.add(new CellEntry(7, 2, CellEntry.KIND_ITEM, 2, 315, 20, 100, false, state, false));
        }
        StoragePanelState captured = StoragePanelState.capture(config(cells, false), 0, 33);
        assertEquals(captured, StoragePanelState.fromTag(captured.toTag()));
        assertFalse(cn.dancingsnow.neoecoae.platform.JdkCompat.first(captured.cells()).installed());
        assertTrue(captured.cells().get(1).installed());
    }

    private static StoragePanelState.TypeLine type(int swatch, long bytes) {
        return new StoragePanelState.TypeLine(swatch, Component.literal("type"), StoragePanelTextures.COLORS[swatch],
            1, 10, bytes, 200, Long.toString(bytes), swatch, BigInteger.valueOf(bytes));
    }

    private static StoragePanelState barState(boolean infinite, double usage, List<StoragePanelState.TypeLine> types) {
        return new StoragePanelState(Component.empty(), 2, true, infinite, false, 0, 100, 0, "150", usage,
            0, 0, List.of(), types, "", 0, false);
    }

    static CellEntry cell(long used, long total) {
        return new CellEntry(7, 1, CellEntry.KIND_ITEM, 2, 315, used, total, total < 0);
    }

    static StorageHostUI.Config config(List<CellEntry> cells, boolean infinite) {
        Component name = Component.translatable("custom.storage").withStyle(style -> style.withColor(0xF89737));
        StorageHostUI.StorageTypeLine type = new StorageHostUI.StorageTypeLine(new ECOCellType(name, 1), 7,
            () -> 2, () -> infinite ? -1 : 315, () -> 50, () -> infinite ? -1 : 100, () -> "999999999999999999999999");
        return new StorageHostUI.Config(() -> Component.translatable("host.title"), () -> 2, () -> true,
            () -> 50, () -> 100, () -> 20, () -> "50", () -> cells, List.of(type),
            () -> infinite, () -> false, () -> false, () -> "test-domain", () -> 0, new ItemStackHandler(1));
    }
}
