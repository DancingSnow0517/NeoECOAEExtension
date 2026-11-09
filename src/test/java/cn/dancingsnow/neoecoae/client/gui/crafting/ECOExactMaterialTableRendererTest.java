package cn.dancingsnow.neoecoae.client.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOExactMaterialTableRendererTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
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
        var rows = ECOExactMaterialTableRenderer.sortMaterials(snapshot.nodes());
        var storedRow = rows.stream().filter(row -> row.key().equals(variant)).findFirst().orElseThrow();
        assertEquals(java.math.BigInteger.valueOf(3), storedRow.fromInventoryBigInteger());
        assertEquals(java.math.BigInteger.ONE, rows.getFirst().missingBigInteger());
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        assertEquals(List.of(appeng.core.localization.GuiText.FromStorage.text("3")),
            renderer.getEntryDescription(storedRow));
        verify(variant).formatAmount(3, AmountFormat.SLOT);
    }

    @Test
    void unverifiedSeedUsesLowerBoundTextInTheMaterialTableAndTooltip() throws Exception {
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
        try (var rendering = mockStatic(AEKeyRendering.class)) {
            rendering.when(() -> AEKeyRendering.getTooltip(key)).thenReturn(new ArrayList<>());
            var tooltip = renderer.getEntryTooltip(node);
            var hint = (net.minecraft.network.chat.contents.TranslatableContents) tooltip.get(1).getContents();
            assertEquals("gui.neoecoae.crafting_report.seed_shortfall_estimate_hint", hint.getKey());
        }
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
        var node = new CraftingGraphSnapshot.MaterialNode(0, key, 1, 0, 0, 0,
            CraftingGraphSnapshot.MaterialStatus.UNSUPPORTED);
        var renderer = mock(ECOExactMaterialTableRenderer.class, CALLS_REAL_METHODS);
        var lines = renderer.getEntryDescription(node);
        assertEquals(1, lines.size());
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) lines.getFirst().getContents();
        assertEquals("gui.neoecoae.crafting_report.quantity_unknown", contents.getKey());
        verify(key, never()).formatAmount(anyLong(), any());
    }

    @Test
    void acyclicMissingPlanRendersGraphInventoryAndShortage() {
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
        try (var rendering = mockStatic(AEKeyRendering.class)) {
            rendering.when(() -> AEKeyRendering.getTooltip(diamond)).thenReturn(new ArrayList<>());
            assertEquals(3, renderer.getEntryTooltip(node).size());
            verify(diamond).formatAmount(21864722041L, AmountFormat.FULL);
            verify(diamond).formatAmount(115481708064L, AmountFormat.FULL);
        }
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
        var menuField = AbstractContainerScreen.class.getDeclaredField("menu");
        menuField.setAccessible(true);
        menuField.set(screen, menu);
        return screen;
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
