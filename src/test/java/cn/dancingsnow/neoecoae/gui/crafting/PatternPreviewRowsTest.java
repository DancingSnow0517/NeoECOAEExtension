package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.BitSet;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternPreviewRowsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void emptyCapacityStartsCompactAndRemovedPatternsKeepTheirSessionCells() {
        var entries = new PatternPreviewEntry[108];
        for (int i = 0; i < entries.length; i++) entries[i] = empty(i < 54 ? 10 : 20, i % 54);
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        assertEquals(0, rows.size());
        assertNull(rows.cell(0));
        entries[53] = entry(10, 53, "iron", "gear");
        entries[107] = entry(20, 53, "gold", "circuit");
        rows.changed(changes(53, 107), false);
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(53, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(107, -1), rows.cell(1));
        assertEquals(53, entries[rows.cell(1).entry()].physicalSlot());
        assertEquals(20, entries[rows.cell(1).entry()].busPosition());
        assertNull(rows.cell(2), "the unused part of the row must not create empty slots");
        entries[53] = empty(10, 53);
        rows.changed(changes(53), false);
        assertEquals(new PatternPreviewRows.Cell(53, -1), rows.cell(0));
        assertTrue(entries[rows.cell(0).entry()].stack().isEmpty());
        assertEquals(new PatternPreviewRows.Cell(107, -1), rows.cell(1));
        entries[107] = empty(20, 53);
        rows.changed(changes(107), false);
        assertEquals(1, rows.size(), "taking everything must leave the current row in place");
        assertTrue(entries[rows.cell(1).entry()].stack().isEmpty());
        var reopened = new PatternPreviewRows();
        reopened.reset(entries);
        assertEquals(0, reopened.size());
        assertNull(reopened.cell(0));
    }

    @Test void directionalSearchPacksOnlyMatchesAndRetainsTheirActualOperationAddress() {
        var entries = new PatternPreviewEntry[18];
        for (int i = 0; i < entries.length; i++) entries[i] = entry(10, i, i == 4 ? "铁锭 iron" : "copper", "gear");
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        rows.filter("铁锭 iron", "");
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(4, -1), rows.cell(0));
        assertEquals(4, entries[rows.cell(0).entry()].physicalSlot());
        assertNull(rows.cell(1));
        entries[4] = entry(10, 4, "gold", "gear");
        rows.changed(changes(4), false);
        assertEquals(new PatternPreviewRows.Cell(4, -1), rows.cell(0), "live replacements keep their source cell");
        rows.filter("gold", "");
        rows.filter("iron", "");
        assertEquals(0, rows.size());
    }

    @Test void diskContainersHaveNoPatternCellsButTheirActualRecipesRetainReadOnlyAddresses() {
        var disk = new PatternPreviewEntry(10, 1, new ItemStack(Items.CHEST), "", "",
                List.of(new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE), "iron", "gear")), true);
        var rows = new PatternPreviewRows();
        var entries = new PatternPreviewEntry[]{entry(10, 0, "gold", "circuit"), disk,
                entry(20, 0, "copper", "plate"),
                new PatternPreviewEntry(20, 1, new ItemStack(Items.CHEST), "", "", List.of(), true)};
        rows.reset(entries);
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(1, 0), rows.cell(1));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(2));
        assertNull(rows.cell(3));
        rows.filter("", "gear");
        assertEquals(new PatternPreviewRows.Cell(1, 0), rows.cell(0));
        assertTrue(entries[rows.cell(0).entry()].auxiliaryDisk());
        rows.filter("gear", "");
        assertEquals(0, rows.size(), "a disk recipe's output must not match an ingredient query");
        entries[1] = new PatternPreviewEntry(10, 1, new ItemStack(Items.CHEST), "", "", List.of(), true);
        rows.changed(changes(1), true);
        rows.filter("", "");
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(1));
        assertNull(rows.cell(2));
    }

    @Test void noSearchRetainsAllOccupiedPhysicalAddresses() {
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{empty(10, 0), entry(20, 0, "iron", "gear"),
                entry(20, 1, "gold", "circuit"), entry(20, 2, "water", "plate")});
        rows.filter("", "");
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(1));
        assertEquals(new PatternPreviewRows.Cell(3, -1), rows.cell(2));
        assertNull(rows.cell(3));
    }

    @Test void directionalQueriesUseAUnionAndIgnoreTheEmptyBranch() {
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{entry(10, 0, "iron ingot 铁锭", "gear"),
                entry(10, 1, "copper", "iron ingot 铁锭"),
                entry(10, 2, "gold", "circuit")});
        rows.filter("IRON 铁锭", "");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertNull(rows.cell(1));
        rows.filter("", "iron");
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(0));
        assertNull(rows.cell(1));
        rows.filter("iron", "circuit");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(1));
        assertNull(rows.cell(2));
        rows.filter("does not exist", "");
        assertEquals(0, rows.size());
        rows.filter(" ", "");
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(2));
    }

    @Test void altShortcutModFiltersRespectIngredientsPrimaryProductsAndDiskRecipes() {
        var disk = new PatternPreviewEntry(10, 2, new ItemStack(Items.CHEST), "", "",
                List.of(new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE),
                        "water\n@minecraft", "coolant\n@mekanism")), true);
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{entry(10, 0, "iron\n@minecraft", "gear\n@extendedae"),
                entry(10, 1, "copper\n@mekanism", "plate\n@minecraft"), disk});
        rows.filter("@MINECRAFT iron", "");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertNull(rows.cell(1));
        rows.filter("", "@minecraft");
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(0));
        assertNull(rows.cell(1), "an ingredient's namespace cannot match a product query");
        rows.filter("@minecraft", "@mekanism");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(2, 0), rows.cell(1));
        assertNull(rows.cell(2));
    }

    @Test void sixRowsAndPartialLastRowsFollowActualPatternCountRatherThanBusCapacity() {
        var entries = new PatternPreviewEntry[630];
        for (int slot = 0; slot < entries.length; slot++) entries[slot] = slot % 10 == 0
                ? entry(10, slot, "material", "product " + slot) : empty(10, slot);
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        assertEquals(7, rows.size());
        assertEquals(new PatternPreviewRows.Cell(530, -1), rows.cell(53));
        assertEquals(new PatternPreviewRows.Cell(620, -1), rows.cell(9 + 53));
        rows.filter("", "product 530");
        assertEquals(new PatternPreviewRows.Cell(530, -1), rows.cell(0));
        assertNull(rows.cell(1));
        entries[530] = empty(10, 530);
        rows.changed(changes(530), false);
        assertEquals(1, rows.size());
        assertEquals(new PatternPreviewRows.Cell(530, -1), rows.cell(0));
        rows.filter("", "missing");
        assertEquals(0, rows.size());
    }

    @Test void newPatternsAppendAndRefilledSourcesReuseTheirOriginalCells() {
        var entries = new PatternPreviewEntry[]{empty(10, 0), entry(10, 1, "iron", "gear"),
                entry(10, 2, "gold", "circuit")};
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        entries[1] = empty(10, 1);
        entries[0] = entry(10, 0, "copper", "plate");
        rows.changed(changes(0, 1), false);
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(0));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(1));
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(2));
        entries[1] = entry(10, 1, "tin", "wire");
        rows.changed(changes(1), false);
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(0));
        assertEquals("wire", entries[rows.cell(0).entry()].outputKeywords());
        assertNull(rows.cell(3));

        var reopened = new PatternPreviewRows();
        reopened.reset(entries);
        assertEquals(new PatternPreviewRows.Cell(0, -1), reopened.cell(0));
        assertEquals(new PatternPreviewRows.Cell(1, -1), reopened.cell(1));
    }

    @Test void fullSnapshotsRebindPhysicalSourcesWithoutReusingRemovedBusCells() {
        var rows = new PatternPreviewRows();
        rows.reset(new PatternPreviewEntry[]{entry(10, 0, "iron", "gear"), entry(20, 0, "gold", "circuit")});
        rows.reset(new PatternPreviewEntry[]{entry(20, 0, "gold", "circuit"), entry(30, 0, "copper", "plate")});
        rows.filter("", "");
        assertNull(rows.cell(0), "a removed bus leaves a hole, never an address belonging to its successor");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(1));
        assertEquals(new PatternPreviewRows.Cell(1, -1), rows.cell(2));
        rows.filter("", "circuit");
        assertEquals(new PatternPreviewRows.Cell(0, -1), rows.cell(0));
        assertNull(rows.cell(1));
    }

    @Test void diskRecipeChangesKeepLaterPhysicalSourcesAndDoNotExposeDiskIcons() {
        var recipe = new PatternPreviewEntry.DiskPattern(new ItemStack(Items.STONE), "iron", "gear");
        var entries = new PatternPreviewEntry[]{entry(10, 0, "gold", "circuit"),
                new PatternPreviewEntry(10, 1, new ItemStack(Items.CHEST), "", "", List.of(recipe, recipe), true),
                entry(20, 0, "copper", "plate")};
        var rows = new PatternPreviewRows();
        rows.reset(entries);
        assertEquals(new PatternPreviewRows.Cell(1, 1), rows.cell(2));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(3));
        entries[1] = new PatternPreviewEntry(10, 1, new ItemStack(Items.CHEST), "", "", List.of(recipe), true);
        rows.changed(changes(1), true);
        assertEquals(new PatternPreviewRows.Cell(1, 0), rows.cell(1));
        assertNull(rows.cell(2));
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(3));

        entries[0] = new PatternPreviewEntry(10, 0, new ItemStack(Items.CHEST), "", "", List.of(recipe), true);
        rows.changed(changes(0), true);
        assertNull(rows.cell(0), "a disk replacing a physical pattern leaves that cell empty");
        assertEquals(new PatternPreviewRows.Cell(2, -1), rows.cell(3));
        assertEquals(new PatternPreviewRows.Cell(0, 0), rows.cell(4));
    }

    private static BitSet changes(int... indexes) {
        var changed = new BitSet();
        for (int index : indexes) changed.set(index);
        return changed;
    }

    private static PatternPreviewEntry empty(long bus, int slot) {
        return new PatternPreviewEntry(bus, slot, ItemStack.EMPTY, "", "", List.of(), false);
    }

    private static PatternPreviewEntry entry(long bus, int slot, String inputs, String output) {
        return new PatternPreviewEntry(bus, slot, new ItemStack(Items.STONE), inputs, output, List.of(), false);
    }
}
