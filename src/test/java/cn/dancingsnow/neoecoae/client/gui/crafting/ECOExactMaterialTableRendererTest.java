package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOExactMaterialTableRendererTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initializeUi();
    }

    @Test
    void unchangedReportReusesMaterialRowsAndFlagsUntilEitherDiagnosticsSnapshotChanges() {
        AEKey seed = mock(AEKey.class, "seed"), fuel = mock(AEKey.class, "fuel");
        var seedNode = new CraftingGraphSnapshot.MaterialNode(0, seed, 100, 0, 0, 1,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var fuelNode = new CraftingGraphSnapshot.MaterialNode(1, fuel, 700, 3, 0, 697,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var nodes = spy(new ArrayList<>(List.of(fuelNode, seedNode)));
        var group = new CraftingGraphSnapshot.CycleGroup(4, List.of(0), List.of(), "INSUFFICIENT_EXTERNAL_INPUT",
            List.of(), List.of(), List.of(new CraftingGraphSnapshot.KeyAmount(seed, 1L)), List.of(), List.of());
        var groups = spy(new ArrayList<>(List.of(group)));
        var fuzzyId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "fuzzy");
        var fuzzy = spy(new ArrayList<>(List.of(fuzzyId)));
        var snapshot = new CraftingGraphSnapshot(0, nodes, List.of(), List.of(), groups, fuzzy,
            new CraftingGraphSnapshot.Summary("CYCLE_UNRESOLVED", 2, 0, 0, 1, 0));
        var entry = new ECOCycleItemList.Entry(seed, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ONE, ExecutionCountKnowledge.UNKNOWN,
            CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, 4);
        var cycles = spy(new ArrayList<>(List.of(entry)));
        var data = new ECOExactMaterialTableRenderer.MaterialData();
        data.update(snapshot, cycles);
        var rows = data.sorted();
        assertEquals(List.of(seedNode, fuelNode), rows);
        assertEquals(List.of(seed, fuel), data.missing());
        assertTrue(data.isMissingStartupSeed(seed));
        assertTrue(data.isEstimatedStartupSeed(seed));
        assertTrue(data.isFuzzyItem(fuzzyId));
        assertTrue(data.hasMaterialDetails());
        assertFalse(data.hasCompleteByteEstimate());

        clearInvocations(nodes, groups, fuzzy, cycles);
        for (int frame = 0; frame < 100; frame++) {
            data.update(snapshot, cycles);
            assertSame(rows, data.sorted());
            assertTrue(data.isCycleParticipant(seed));
            assertTrue(data.hasEstimatedStartupSeed());
            assertTrue(data.containsComponent(4));
            assertEquals(2, data.missing().size());
        }
        verifyNoInteractions(nodes, groups, fuzzy, cycles);

        var solvedCycles = List.of(new ECOCycleItemList.Entry(seed, 1, 100, 4));
        data.update(snapshot, solvedCycles);
        assertEquals(List.of(fuelNode, seedNode), data.sorted());
        assertFalse(data.isMissingStartupSeed(seed));
        assertFalse(data.hasEstimatedStartupSeed());
        assertTrue(data.isCycleSeed(seed));
        assertFalse(data.isCycleSeed(fuel));

        var suppliedFuel = new CraftingGraphSnapshot.MaterialNode(1, fuel, 700, 700, 0, 0,
            CraftingGraphSnapshot.MaterialStatus.SATISFIED);
        var updated = new CraftingGraphSnapshot(0, List.of(seedNode, suppliedFuel), List.of(), List.of(), groups,
            List.of(), new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 2, 0, 0, 1, 0));
        data.update(updated, solvedCycles);
        assertEquals(List.of(seed), data.missing());
        assertEquals(List.of(seedNode, suppliedFuel), data.sorted());
        assertFalse(data.isFuzzyItem(fuzzyId));
        data.update(CraftingGraphSnapshot.EMPTY, List.of());
        assertTrue(data.sorted().isEmpty());
        assertFalse(data.isCycleParticipant(seed));
        assertFalse(data.containsComponent(4));
    }

    @Test
    void sortingPreparesSeedFlagsAndWideAmountsOncePerMaterial() {
        AEKey key = mock(AEKey.class);
        var nodes = new ArrayList<CraftingGraphSnapshot.MaterialNode>();
        BigInteger first = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        for (int index = 0; index < 256; index++) {
            String amount = first.add(BigInteger.valueOf(index)).toString();
            nodes.add(spy(new CraftingGraphSnapshot.MaterialNode(index, key, Long.MAX_VALUE, 0, 0,
                Long.MAX_VALUE, CraftingGraphSnapshot.MaterialStatus.MISSING,
                amount, "0", "0", amount, "0", "0")));
        }
        var seedChecks = new java.util.concurrent.atomic.AtomicInteger();
        var sorted = ECOExactMaterialTableRenderer.sortMaterials(nodes, ignored -> {
            seedChecks.incrementAndGet();
            return false;
        });
        assertEquals(nodes.size(), seedChecks.get(), "Seed flags must not be recomputed by the comparator");
        assertEquals(255, sorted.get(0).nodeId());
        assertEquals(0, sorted.get(255).nodeId());
        for (var node : nodes) {
            verify(node, times(1)).missingBigInteger();
            verify(node, times(1)).toCraftBigInteger();
            verify(node, times(1)).fromInventoryBigInteger();
        }
    }

    @Test
    void reservedComponentVariantIsListedAlongsideTheMissingTemplate() {
        AEKey template = mock(AEKey.class);
        AEKey variant = mock(AEKey.class);
        AEKey output = mock(AEKey.class);
        when(template.getPrimaryKey()).thenReturn(new Object());
        when(variant.getPrimaryKey()).thenReturn(new Object());
        when(output.getPrimaryKey()).thenReturn(new Object());
        when(variant.formatAmount(anyLong(), any())).thenAnswer(call -> Long.toString(call.getArgument(0)));
        var used = new appeng.api.stacks.KeyCounter(); used.add(variant, 3);
        var missing = new appeng.api.stacks.KeyCounter(); missing.add(template, 1);
        var plan = new appeng.crafting.CraftingPlan(new appeng.api.stacks.GenericStack(output, 4), 0,
            true, false, used, new appeng.api.stacks.KeyCounter(), missing, java.util.Map.of());
        var result = new cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult(
            PlanningStatus.MISSING_ITEMS, plan,
            new cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace(), List.of(), 0);
        var snapshot = cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory.create(result);
        var rows = ECOExactMaterialTableRenderer.sortMaterials(snapshot.nodes(), key -> false);
        var storedRow = rows.stream().filter(row -> row.key().equals(variant)).findFirst().orElseThrow();
        assertEquals(java.math.BigInteger.valueOf(3), storedRow.fromInventoryBigInteger());
        assertEquals(java.math.BigInteger.ONE, rows.getFirst().missingBigInteger());
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        assertEquals(List.of(appeng.core.localization.GuiText.FromStorage.text("3")),
            renderer.getEntryDescription(storedRow));
        verify(variant).formatAmount(3, AmountFormat.SLOT);
    }

    @Test
    void unverifiedSeedUsesLowerBoundTextInTheMaterialTableAndSharedCard() throws Exception {
        AEKey key = mock(AEKey.class);
        when(key.formatAmount(anyLong(), any())).thenAnswer(call -> Long.toString(call.getArgument(0)));
        var node = new CraftingGraphSnapshot.MaterialNode(0, key, 1, 0, 0, 1,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        var predicate = ECOExactMaterialTableRenderer.class.getDeclaredField("estimatedStartupSeed");
        predicate.setAccessible(true);
        predicate.set(renderer, (java.util.function.Predicate<AEKey>) candidate -> candidate.equals(key));
        var description = renderer.getEntryDescription(node);
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) description.getFirst().getContents();
        assertEquals("gui.neoecoae.crafting_report.seed_shortfall_estimate", contents.getKey());
        assertArrayEquals(new Object[] {"1"}, contents.getArgs());
        var card = queuedMaterial(renderer, node);
        assertTrue(card.estimatedSeed());
        assertEquals(BigInteger.ONE, card.missing());
    }

    @Test
    void materialAmountsContinuePastQAndRespectFluidUnits() throws Exception {
        var formatAmount = ECOExactMaterialTableRenderer.class.getDeclaredMethod(
            "formatAmount", AEKey.class, java.math.BigInteger.class, AmountFormat.class);
        formatAmount.setAccessible(true);
        AEKey key = mock(AEKey.class);
        when(key.getAmountPerUnit()).thenReturn(1);
        int[] exponents = {30, 33, 36, 60, 63, 123};
        String[] expected = {"1Q", "1KQ", "1MQ", "1QQ", "1×10^63", "1×10^123"};
        for (int i = 0; i < exponents.length; i++) {
            assertEquals(expected[i], formatAmount.invoke(null, key,
                java.math.BigInteger.TEN.pow(exponents[i]), AmountFormat.SLOT));
        }
        when(key.getAmountPerUnit()).thenReturn(1000);
        assertEquals("1KQ", formatAmount.invoke(null, key,
            java.math.BigInteger.TEN.pow(36), AmountFormat.SLOT));
        verify(key, never()).formatAmount(anyLong(), any());
    }

    @Test
    void unsupportedShellUsesDiagnosticsButNativeFallbackKeepsItsMaterials() {
        assertTrue(ECOExactMaterialTableRenderer.isDiagnosticShell(PlanningStatus.PARTIAL_UNSUPPORTED, true, true));
        assertTrue(ECOExactMaterialTableRenderer.isDiagnosticShell(PlanningStatus.INTERNAL_ERROR, true, true));
        assertFalse(ECOExactMaterialTableRenderer.isDiagnosticShell(PlanningStatus.PARTIAL_UNSUPPORTED, true, false));
        assertFalse(ECOExactMaterialTableRenderer.isDiagnosticShell(PlanningStatus.PARTIAL_UNSUPPORTED, false, true));
        assertFalse(ECOExactMaterialTableRenderer.isDiagnosticShell(PlanningStatus.MISSING_ITEMS, true, true));
        assertFalse(ECOExactMaterialTableRenderer.isDiagnosticShell(null, true, true));
    }

    @Test
    void structuralMaterialWithoutSolvedAmountsShowsUnknown() {
        AEKey key = mock(AEKey.class);
        var node = new CraftingGraphSnapshot.MaterialNode(0, key, 0, 0, 0, 0,
            CraftingGraphSnapshot.MaterialStatus.UNSUPPORTED);
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        var lines = renderer.getEntryDescription(node);
        assertEquals(1, lines.size());
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) lines.getFirst().getContents();
        assertEquals("gui.neoecoae.crafting_report.quantity_unknown", contents.getKey());
        verify(key, never()).formatAmount(anyLong(), any());
    }

    @Test
    void requestedAmountRemainsVisibleWithoutInventingMaterialOrByteTotals() {
        AEKey key = mock(AEKey.class);
        long amount = 5_000_000_000_000_000_000L;
        when(key.formatAmount(amount, AmountFormat.SLOT)).thenReturn("5E");
        var node = new CraftingGraphSnapshot.MaterialNode(0, key, amount, 0, 0, 0,
            CraftingGraphSnapshot.MaterialStatus.CYCLE);
        var snapshot = new CraftingGraphSnapshot(0, List.of(node), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("CYCLE_UNRESOLVED", 1, 0, 0, 0, 0));
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        var contents = (net.minecraft.network.chat.contents.TranslatableContents)
            renderer.getEntryDescription(node).get(0).getContents();
        assertEquals("gui.neoecoae.crafting_graph.details.requested", contents.getKey());
        assertArrayEquals(new Object[] {"5E"}, contents.getArgs());
        assertFalse(snapshot.hasMaterialDetails());
        assertFalse(snapshot.hasCompleteByteEstimate());
    }

    @Test
    void startupSeedPrecedesLargerExternalShortagesWithoutHidingEither() {
        AEKey seed = mock(AEKey.class, "seed"), fuel = mock(AEKey.class, "fuel");
        var seedNode = new CraftingGraphSnapshot.MaterialNode(0, seed, 5_000_000_000_000_000_000L, 0, 0, 1,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var fuelNode = new CraftingGraphSnapshot.MaterialNode(1, fuel, 5_000_000_000_000_000_000L,
            0, 0, 5_000_000_000_000_000_000L, CraftingGraphSnapshot.MaterialStatus.MISSING);
        var rows = ECOExactMaterialTableRenderer.sortMaterials(List.of(fuelNode, seedNode), seed::equals);
        assertEquals(List.of(seedNode, fuelNode), rows);
        var cycle = new CraftingGraphSnapshot.CycleGroup(0, List.of(0), List.of(), "INSUFFICIENT_EXTERNAL_INPUT",
            List.of(), List.of(), List.of(new CraftingGraphSnapshot.KeyAmount(seed, 1L)), List.of(), List.of());
        var snapshot = new CraftingGraphSnapshot(0, rows, List.of(), List.of(), List.of(cycle), List.of(),
            new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 2, 0, 0, 1, 0));
        assertTrue(snapshot.hasMaterialDetails());
        assertFalse(snapshot.hasCompleteByteEstimate(),
            "An unknown firing vector has no full-order byte estimate");
    }

    @Test
    void completeMissingCycleKeepsItsByteEstimateEvenThoughItCannotExecute() {
        AEKey fuel = mock(AEKey.class);
        var node = new CraftingGraphSnapshot.MaterialNode(0, fuel, 700, 3, 0, 697,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        for (String status : List.of("MISSING_ITEMS", "PLANNED_BUT_AMOUNT_UNREPRESENTABLE", "PARTIAL")) {
            for (ExecutionCountKnowledge knowledge : ExecutionCountKnowledge.values()) {
                var cycle = new CraftingGraphSnapshot.CycleGroup(4, List.of(0), List.of(),
                    "INSUFFICIENT_EXTERNAL_INPUT", List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), knowledge,
                    CycleSolveStatus.SUCCESS);
                var snapshot = new CraftingGraphSnapshot(0, List.of(node), List.of(), List.of(), List.of(cycle),
                    List.of(), new CraftingGraphSnapshot.Summary(status, 1, 0, 0, 1, 0));
                assertEquals(knowledge == ExecutionCountKnowledge.EXACT && !status.equals("PARTIAL"),
                    snapshot.hasCompleteByteEstimate());
            }
        }
    }

    @Test
    void acyclicMissingPlanRendersGraphInventoryAndShortage() throws Exception {
        AEKey diamond = mock(AEKey.class);
        when(diamond.formatAmount(anyLong(), any())).thenAnswer(call -> Long.toString(call.getArgument(0)));
        var node = new CraftingGraphSnapshot.MaterialNode(0, diamond, 137346430105L,
            21864722041L, 0, 115481708064L, CraftingGraphSnapshot.MaterialStatus.MISSING);
        var snapshot = new CraftingGraphSnapshot(0, List.of(node), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 1, 0, 0, 0, 0));
        assertTrue(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.MISSING_ITEMS, snapshot));
        assertTrue(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.CYCLE_UNRESOLVED, snapshot));
        assertFalse(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.CYCLE_UNRESOLVED,
            CraftingGraphSnapshot.EMPTY));
        assertFalse(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.MISSING_ITEMS,
            CraftingGraphSnapshot.EMPTY));
        assertFalse(ECOExactMaterialTableRenderer.hasMissingMaterialSnapshot(PlanningStatus.PARTIAL_UNSUPPORTED, snapshot));
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        assertEquals(2, renderer.getEntryDescription(node).size());
        verify(diamond).formatAmount(21864722041L, AmountFormat.SLOT);
        verify(diamond).formatAmount(115481708064L, AmountFormat.SLOT);
        verify(diamond, never()).formatAmount(137346430105L, AmountFormat.SLOT);
        assertEquals(0x1AFF0000, renderer.getEntryOverlayColor(node));
        var card = queuedMaterial(renderer, node);
        assertEquals(BigInteger.valueOf(21864722041L), card.stored());
        assertEquals(BigInteger.valueOf(115481708064L), card.missing());
        assertEquals(BigInteger.valueOf(137346430105L), card.requested());
    }

    @Test
    void originalMaterialCellShowsStockCraftingAndDeficitTogether() {
        AEKey material = mock(AEKey.class);
        when(material.formatAmount(anyLong(), any())).thenAnswer(call -> Long.toString(call.getArgument(0)));
        var node = new CraftingGraphSnapshot.MaterialNode(0, material, 100L, 25L, 60L, 15L,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        var expected = List.of(appeng.core.localization.GuiText.FromStorage.text("25"),
            appeng.core.localization.GuiText.Missing.text("15"), appeng.core.localization.GuiText.ToCraft.text("60"));
        assertEquals(expected, renderer.getEntryDescription(node));
    }

    @Test
    void exactReportBookmarksEveryGraphShortageWhenNativeSummaryListsOnlySome() throws Exception {
        AEKey diamond = mock(AEKey.class, "diamond");
        AEKey netherrack = mock(AEKey.class, "netherrack");
        var nodes = List.of(
            new CraftingGraphSnapshot.MaterialNode(0, diamond, 28, 20, 0, 8,
                CraftingGraphSnapshot.MaterialStatus.MISSING),
            new CraftingGraphSnapshot.MaterialNode(1, netherrack, 4, 0, 0, 4,
                CraftingGraphSnapshot.MaterialStatus.MISSING));
        var summary = new CraftingPlanSummary(0, true, List.of(
            new CraftingPlanSummaryEntry(diamond, 0, 8, 0),
            new CraftingPlanSummaryEntry(netherrack, 4, 0, 0)));
        for (PlanningStatus status : List.of(PlanningStatus.MISSING_ITEMS, PlanningStatus.CYCLE_UNRESOLVED)) {
            var snapshot = new CraftingGraphSnapshot(0, nodes, List.of(), List.of(), List.of(), List.of(),
                new CraftingGraphSnapshot.Summary(status.name(), 2, 0, 0, 0, 0));
            var screen = reportScreen(summary, snapshot, status);
            assertEquals(Set.of(diamond, netherrack), Set.copyOf(bookmarkedKeys(screen)));
            assertTrue(hasMissingMaterials(screen));
        }
    }

    @Test
    void nativeFallbackBookmarksItsSummaryAndIgnoresDiagnosticShortages() throws Exception {
        AEKey nativeKey = mock(AEKey.class, "native material");
        AEKey diagnosticKey = mock(AEKey.class, "diagnostic material");
        var snapshot = new CraftingGraphSnapshot(0, List.of(
            new CraftingGraphSnapshot.MaterialNode(0, diagnosticKey, 1, 0, 0, 1,
                CraftingGraphSnapshot.MaterialStatus.MISSING)), List.of(), List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("PARTIAL_UNSUPPORTED", 1, 0, 0, 0, 0));
        for (long missing : List.of(0L, 3L)) {
            var summary = new CraftingPlanSummary(0, true,
                List.of(new CraftingPlanSummaryEntry(nativeKey, missing, 0, 0)));
            var screen = reportScreen(summary, snapshot, PlanningStatus.PARTIAL_UNSUPPORTED);
            assertEquals(missing > 0 ? List.of(nativeKey) : List.of(), bookmarkedKeys(screen));
            assertEquals(missing > 0, hasMissingMaterials(screen));
        }
    }

    private static ECOCraftConfirmScreen reportScreen(
            CraftingPlanSummary summary, CraftingGraphSnapshot snapshot, PlanningStatus status) throws Exception {
        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        when(menu.getPlan()).thenReturn(summary);
        var mode = (ECOCraftConfirmMenuMode) menu;
        when(mode.neoecoae$getPlanningStatus()).thenReturn(status);
        when(mode.neoecoae$getCraftingGraphSnapshot()).thenReturn(snapshot);
        when(mode.neoecoae$getCycleItems()).thenReturn(List.of());
        var screen = mock(ECOCraftConfirmScreen.class, CALLS_REAL_METHODS);
        var materialData = ECOCraftConfirmScreen.class.getDeclaredField("materialData");
        materialData.setAccessible(true);
        materialData.set(screen, new ECOExactMaterialTableRenderer.MaterialData());
        var menuField = AbstractContainerScreen.class.getDeclaredField("menu");
        menuField.setAccessible(true);
        menuField.set(screen, menu);
        return screen;
    }

    private static ECOCraftReportTooltips.Material queuedMaterial(ECOExactMaterialTableRenderer renderer,
            CraftingGraphSnapshot.MaterialNode node) throws Exception {
        var screen = mock(ECOCraftConfirmScreen.class);
        var field = appeng.client.gui.me.crafting.AbstractTableRenderer.class.getDeclaredField("screen");
        field.setAccessible(true);
        field.set(renderer, screen);
        assertTrue(renderer.getEntryTooltip(node).isEmpty());
        var card = org.mockito.ArgumentCaptor.forClass(ECOCraftReportTooltips.Material.class);
        verify(screen).queueMaterialTooltip(card.capture());
        return card.getValue();
    }

    @SuppressWarnings("unchecked")
    private static List<AEKey> bookmarkedKeys(ECOCraftConfirmScreen screen) throws Exception {
        var collect = ECOCraftConfirmScreen.class.getDeclaredMethod("missingKeys");
        collect.setAccessible(true);
        return (List<AEKey>) collect.invoke(screen);
    }

    private static boolean hasMissingMaterials(ECOCraftConfirmScreen screen) throws Exception {
        var hasMissing = ECOCraftConfirmScreen.class.getDeclaredMethod("hasMissingMaterials");
        hasMissing.setAccessible(true);
        return (boolean) hasMissing.invoke(screen);
    }
}
