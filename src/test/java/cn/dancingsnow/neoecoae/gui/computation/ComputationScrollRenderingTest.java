package cn.dancingsnow.neoecoae.gui.computation;

import appeng.client.api.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.core.AEConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComputationScrollRenderingTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
            AccessorRegistries.init();
        }
    }

    private org.mockito.MockedStatic<appeng.api.stacks.AEKeyTypesInternal> keyTypes;

    private Object previousMissingContentItem;

    @org.junit.jupiter.api.BeforeEach void registerKeyTypes() throws Exception {
        keyTypes = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class);
        keyTypes.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
            .thenReturn(cn.dancingsnow.neoecoae.gui.common.GuiTestKeys.REGISTRY);
        // AEKey encoding consults the deferred fallback item, absent in an isolated unit runner.
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        previousMissingContentItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missingItem = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(missingItem.get()).thenReturn(Items.BARRIER);
        when(missingItem.asItem()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);
    }

    @org.junit.jupiter.api.AfterEach void closeKeyTypes() throws Exception {
        keyTypes.close();
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
    }

    @Test void statusRetainsBothPagesAndIgnoresEarlierScrollReplies() throws Exception {
        // 26.1: LDLib2 DataBindingBuilder.build() needs a side; these are client-side views.
        try (var ldlibSide = org.mockito.Mockito.mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlibSide.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
            panel.setValue(snapshot(0, 0, 1), false);
            scroll(panel, "scrollCpuTo", 3);
            scroll(panel, "scrollCpuTo", 6);
            scroll(panel, "scrollItemsTo", 3);
            scroll(panel, "scrollItemsTo", 6);
            assertDrawn(panel, 1);

            panel.setValue(snapshot(3, 3, 4, 1, 1), false);
            assertEquals(6, field(panel, "cpuOffset"));
            assertEquals(6, field(panel, "itemOffset"));
            assertDrawn(panel, 1);

        panel.setValue(snapshot(6, 6, 7, 2, 2), false);
        assertFalse((boolean) field(panel, "cpuScrollPending"));
        assertFalse((boolean) field(panel, "itemScrollPending"));
        assertDrawn(panel, 7);
        }
    }

    @Test void selectingAnotherCpuClearsAnUnfinishedItemScroll() throws Exception {
        var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
        panel.setValue(snapshot(0, 0, 1), false);
        scroll(panel, "scrollItemsTo", 6);
        panel.open(2);
        var response = snapshot(0, 0, 2);
        response.putLong("selectionSequence", 1);
        panel.setValue(response, false);
        assertEquals(0, field(panel, "itemOffset"));
        assertFalse((boolean) field(panel, "itemScrollPending"));
    }

    @Test void rapidCpuSelectionCannotBeOverwrittenByAnEarlierReply() throws Exception {
        var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
        panel.setValue(snapshot(0, 0, 1), false);
        panel.open(2);
        panel.open(3);
        var earlier = snapshot(0, 0, 2);
        earlier.putLong("selectionSequence", 1);
        panel.setValue(earlier, false);
        assertEquals(3, field(panel, "selectedSerial"), "the latest click owns the highlight and outgoing request");
        var request = (com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue<?>) field(panel, "selectionRequest");
        assertEquals(3, ((CompoundTag) request.getValue()).getIntOr("serial", 0));
        assertEquals(2, ((CompoundTag) request.getValue()).getLongOr("sequence", 0L));
        assertEquals(0, field(panel, "itemTotal"), "do not expose another CPU's items while selection is pending");

        var latest = snapshot(0, 0, 3);
        latest.putLong("selectionSequence", 2);
        panel.setValue(latest, false);
        assertEquals(3, field(panel, "selectedSerial"));
        assertEquals(60, field(panel, "itemTotal"));
        panel.setValue(earlier, false);
        assertEquals(3, field(panel, "selectedSerial"), "late replies stay stale after the latest acknowledgement");
    }

    @Test void returningToTheSameCpuCannotAcknowledgeAnOlderSelection() throws Exception {
        var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
        var original = snapshot(0, 0, 1);
        panel.setValue(original, false);
        panel.open(2);
        panel.open(1);
        panel.setValue(original.copy(), false);
        assertEquals(0, field(panel, "itemTotal"), "serial equality cannot acknowledge an A-B-A switch");
        var latest = original.copy();
        latest.putLong("selectionSequence", 2);
        panel.setValue(latest, false);
        assertEquals(60, field(panel, "itemTotal"));
    }

    @Test void clickingTheSelectedCpuKeepsAnUnfinishedItemScroll() throws Exception {
        var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
        panel.setValue(snapshot(0, 0, 1), false);
        scroll(panel, "scrollItemsTo", 6);
        panel.open(1);
        assertTrue((boolean) field(panel, "itemScrollPending"));
        assertEquals(6, field(panel, "itemOffset"));
    }

    @Test void sidebarKeepsItsPageVisibleUntilTheLatestScrollReply() throws Exception {
        // 26.1: LDLib2 DataBindingBuilder.build() needs a side; these are client-side views.
        try (var ldlibSide = org.mockito.Mockito.mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlibSide.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            var selector = new ComputationCpuSelector(config(), ignored -> {}, new CpuSelectionState.Identities<>());
            selector.setValue(sidebarSnapshot(0, 1, 0), false);
            scroll(selector, "scrollTo", 2);
            scroll(selector, "scrollTo", 4);
            assertDrawn(selector, 1);
            selector.setValue(sidebarSnapshot(2, 3, 1), false);
            assertEquals(4, field(selector, "offset"));
            assertDrawn(selector, 1);
            selector.setValue(sidebarSnapshot(4, 5, 2), false);
            assertDrawn(selector, 5);
        }
    }

    @Test void returningToTheOriginalOffsetCannotAcknowledgeAnOldSnapshot() throws Exception {
        // 26.1: LDLib2 DataBindingBuilder.build() needs a side; these are client-side views.
        try (var ldlibSide = org.mockito.Mockito.mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlibSide.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
            panel.setValue(snapshot(0, 0, 1), false);
            scroll(panel, "scrollCpuTo", 3);
            scroll(panel, "scrollCpuTo", 0);
            panel.setValue(snapshot(0, 0, 1), false);
            assertTrue((boolean) field(panel, "cpuScrollPending"));
            panel.setValue(snapshot(3, 0, 4, 1, 0), false);
            assertEquals(0, field(panel, "cpuOffset"));
            assertDrawn(panel, 1);
            panel.setValue(snapshot(0, 0, 1, 2, 0), false);
            assertFalse((boolean) field(panel, "cpuScrollPending"));
        }
    }

    @Test void tooltipCollectionUsesTheUiMousePositionWithoutCoordinatesInTheEvent() throws Exception {
        try (var ldlib = mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlib.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
            var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
            var ui = com.lowdragmc.lowdraglib2.gui.ui.ModularUI.of(com.lowdragmc.lowdraglib2.gui.ui.UI.of(panel));
            ui.init(ComputationCpuStatusPanel.WIDTH, ComputationCpuStatusPanel.HEIGHT);
            var snapshot = snapshot(0, 0, 1);
            var item = new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), new BigInteger("123456789012345678901"),
                BigInteger.ONE, BigInteger.TEN, true);
            snapshot.getListOrEmpty("items").set(0, item.write(config().registries()));
            panel.setValue(snapshot, false);
            assertDrawn(panel, 1);

            try (var minecraft = cardClient()) {
            var cpuHitbox = panel.hitTest(panel.getPositionX() + 12, panel.getPositionY() + 28).getA();
            assertNotSame(panel, cpuHitbox);
            set(ui, "lastMouseX", panel.getPositionX() + 12);
            set(ui, "lastMouseY", panel.getPositionY() + 28);
            set(ui, "lastHoveredElement", cpuHitbox);
            var cpuTooltip = cpuHitbox.collectHoverTooltips();
            assertNotNull(cpuTooltip);
            assertTrue(cn.dancingsnow.neoecoae.gui.common.UiTestRendering.tooltipTexts(cpuTooltip).get(0).getString().startsWith("CPU 1"));
            assertNotNull(cn.dancingsnow.neoecoae.gui.common.UiTestRendering.tooltipComponent(cpuTooltip));

            var itemHitbox = panel.hitTest(panel.getPositionX() + 106, panel.getPositionY() + 28).getA();
            assertNotSame(panel, itemHitbox);
            assertNotSame(cpuHitbox, itemHitbox);
            set(ui, "lastMouseX", panel.getPositionX() + 106);
            set(ui, "lastHoveredElement", itemHitbox);
            try (var keys = mockStatic(AEKeyRendering.class)) {
                keys.when(() -> AEKeyRendering.getTooltip(item.key()))
                    .thenReturn(List.of(net.minecraft.network.chat.Component.literal("Stone")));
                var tooltip = itemHitbox.collectHoverTooltips();
                assertNotNull(tooltip);
                assertEquals(List.of(net.minecraft.network.chat.Component.literal("Stone").withStyle(
                    net.minecraft.ChatFormatting.BOLD)), cn.dancingsnow.neoecoae.gui.common.UiTestRendering.tooltipTexts(tooltip));
                assertNotNull(cn.dancingsnow.neoecoae.gui.common.UiTestRendering.tooltipComponent(tooltip));
                // The one-pixel separator must stay empty after scrolling stops interpolating rows.
                set(ui, "lastMouseY", panel.getPositionY() + 27 + 22);
                assertNull(itemHitbox.collectHoverTooltips());
            }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jei"})
    void recipeLookupFollowsTheRenderedItemAndTaskPages(String viewer) throws Exception {
        var previousIngredients = LDLibJEIPlugin.ingredientManager;
        try (var fonts = mockStatic(com.lowdragmc.lowdraglib2.gui.LDLibFonts.class);
             var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            ldlib.when(() -> LDLib2.id(anyString())).thenAnswer(call ->
                Identifier.fromNamespaceAndPath("ldlib2", call.getArgument(0)));
            com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.init();
            var font = mock(Font.class, RETURNS_DEEP_STUBS);
            set(font, "lineHeight", 9);

            ldlib.when(LDLib2::isClient).thenReturn(true);
            ldlib.when(LDLib2::isJeiLoaded).thenReturn(viewer.equals("jei"));
            ldlib.when(LDLib2::isEmiLoaded).thenReturn(viewer.equals("emi"));
            var factory = viewer.equals("jei") ? jeiFactory() : null;
            var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
            ldlib.when(LDLib2::isClient).thenReturn(false);
            var ui = ModularUI.of(UI.of(panel));
            ui.init(ComputationCpuStatusPanel.WIDTH, ComputationCpuStatusPanel.HEIGHT);
            var snapshot = snapshot(0, 0, 1);
            snapshot.getListOrEmpty("items").add(new ComputationCpuItemEntry(
                AEFluidKey.of(Fluids.WATER), BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO, false)
                .write(config().registries()));
            var tasks = new ListTag();
            tasks.add(new ComputationCpuItemEntry(AEItemKey.of(Items.DIAMOND), BigInteger.ZERO,
                BigInteger.ONE, BigInteger.ZERO, false).write(config().registries()));
            snapshot.put("tasks", tasks);
            snapshot.putInt("taskTotal", 1);
            panel.setValue(snapshot, false);
            assertDrawn(panel, 1);

            assertSame(Items.STONE, recipeAt(panel, ui, viewer, factory, 106, 28));
            assertSame(Fluids.WATER, recipeAt(panel, ui, viewer, factory, 174, 28));
            assertSame(Items.DIAMOND, recipeAt(panel, ui, viewer, factory, 334, 28));
            assertNull(recipeAt(panel, ui, viewer, factory, 172, 28), "column separator");
            assertNull(recipeAt(panel, ui, viewer, factory, 106, 49), "row separator");
            assertNull(recipeAt(panel, ui, viewer, factory, 242, 28), "empty cell");

            scroll(panel, "scrollItemsTo", 3);
            var next = snapshot(0, 3, 1, 0, 1);
            var nextItems = next.getListOrEmpty("items");
            nextItems.add(nextItems.get(0).copy());
            nextItems.set(0, new ComputationCpuItemEntry(AEItemKey.of(Items.GOLD_INGOT), BigInteger.ONE,
                BigInteger.ZERO, BigInteger.ZERO, false).write(config().registries()));
            panel.setValue(next, false);
            assertSame(Items.STONE, recipeAt(panel, ui, viewer, factory, 106, 28), "keep the visible page until redraw");
            assertDrawn(panel, 1);
            assertSame(Items.GOLD_INGOT, recipeAt(panel, ui, viewer, factory, 106, 28));

            var search = panel.selectId("computation-status-search").findFirst().orElseThrow();
            set(ui, "focusedElement", search);
            assertNull(recipeAt(panel, ui, viewer, factory, 106, 28), "typing R must stay in the search field");
            set(ui, "focusedElement", null);
            ((UIElement) field(panel, "itemActionsPopup")).setDisplay(true);
            ui.init(ComputationCpuStatusPanel.WIDTH, ComputationCpuStatusPanel.HEIGHT);
            assertNull(recipeAt(panel, ui, viewer, factory, 106, 28), "a popup must not expose the item behind it");
        } finally {
            LDLibJEIPlugin.ingredientManager = previousIngredients;
        }
    }

    /** Use the selected JEI runtime's real ingredient and clickable builders, with only its registry boundary mocked. */
    private static IClickableIngredientFactory jeiFactory() throws Exception {
        var createTyped = Class.forName("mezz.jei.common.ingredients.TypedIngredient")
            .getMethod("createUnvalidated", IIngredientType.class, Object.class);
        var ingredients = mock(IIngredientManager.class);
        when(ingredients.createTypedIngredient(any(), any(), eq(false)))
            .thenAnswer(call -> Optional.of(createTyped.invoke(null, call.getArgument(0), call.getArgument(1))));
        LDLibJEIPlugin.ingredientManager = ingredients;
        var factoryType = Class.forName("mezz.jei.common.ingredients.ITypedIngredientFactory");
        return (IClickableIngredientFactory) Class.forName("mezz.jei.common.input.ClickableIngredientFactory")
            .getConstructor(factoryType).newInstance(mock(factoryType, org.mockito.AdditionalAnswers.returnsFirstArg()));
    }

    private static Object recipeAt(ComputationCpuStatusPanel panel, ModularUI ui, String viewer,
            IClickableIngredientFactory factory, int localX, int localY) throws Exception {
        float x = panel.getPositionX() + localX, y = panel.getPositionY() + localY;
        var target = panel.hitTest(x, y).getA();
        set(ui, "lastMouseX", x);
        set(ui, "lastMouseY", y);
        set(ui, "lastHoveredElement", target);
        var event = UIEvent.create(JEIUIEvents.CLICKABLE_INGREDIENT);
        event.target = target;
        event.x = x;
        event.y = y;
        event.customData = factory;
        UIEventDispatcher.dispatchEvent(event);
        if (event.customData instanceof Optional<?> result && result.orElse(null) instanceof IClickableIngredient<?> clickable) {
            var ingredient = clickable.getTypedIngredient().getIngredient();
            if (ingredient instanceof ItemStack item) return item.getItem();
            if (ingredient instanceof FluidStack fluid) return fluid.getFluid();
        }
        return null;
    }

    /** Card tooltips measure text with the client font; the window stays absent so Shift reads as released. */
    private static org.mockito.MockedStatic<net.minecraft.client.Minecraft> cardClient() throws Exception {
        var mocked = mockStatic(net.minecraft.client.Minecraft.class);
        var client = mock(net.minecraft.client.Minecraft.class);
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(any(net.minecraft.network.chat.FormattedText.class))).thenReturn(10);
        var fontField = net.minecraft.client.Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(client, font);
        mocked.when(net.minecraft.client.Minecraft::getInstance).thenReturn(client);
        return mocked;
    }

    private static ComputationControllerUI.Config config() {
        return new ComputationControllerUI.Config(
            () -> true, () -> 1, () -> 0, () -> 0,
            () -> 0L, () -> 1024L, () -> 1024L,
            () -> 0, () -> 8, () -> 4, () -> 16,
            () -> CpuSelectionMode.ANY, ignored -> {},
            () -> false, () -> 3, () -> {},
            () -> true, () -> {}, () -> true, () -> {},
            () -> 1, ignored -> {},
            () -> net.minecraft.world.item.ItemStack.EMPTY, List::of, List::of, null,
            (position, expected, toInventory) -> false, (position, expected) -> false,
            () -> 0L, () -> true, List::of, cpu -> {}, cpu -> {},
            () -> net.minecraft.network.chat.Component.empty(), () -> true, () -> {});
    }

    private static CompoundTag snapshot(int cpuOffset, int itemOffset, int serial) {
        return snapshot(cpuOffset, itemOffset, serial, 0, 0);
    }

    private static CompoundTag snapshot(int cpuOffset, int itemOffset, int serial,
            long cpuSequence, long itemSequence) {
        var snapshot = new CompoundTag();
        snapshot.putInt("total", 30);
        snapshot.putInt("cpuOffset", cpuOffset);
        snapshot.putInt("cpuPageStart", cpuOffset);
        snapshot.putInt("selectedSerial", serial);
        snapshot.putInt("itemTotal", 60);
        snapshot.putInt("itemOffset", itemOffset);
        snapshot.putInt("itemPageStart", itemOffset);
        snapshot.putLong("cpuScrollSequence", cpuSequence);
        snapshot.putLong("itemScrollSequence", itemSequence);
        var cpus = new ListTag();
        cpus.add(cpu(serial).write(config().registries()));
        snapshot.put("cpus", cpus);
        var items = new ListTag();
        items.add(new ComputationCpuItemEntry(AEItemKey.of(Items.STONE), BigInteger.ONE,
            BigInteger.ZERO, BigInteger.ZERO, false).write(config().registries()));
        snapshot.put("items", items);
        return snapshot;
    }

    private static CompoundTag sidebarSnapshot(int offset, int serial, long sequence) {
        var snapshot = snapshot(offset, 0, serial);
        snapshot.putInt("offset", offset);
        snapshot.putLong("scrollSequence", sequence);
        snapshot.put("page", snapshot.getListOrEmpty("cpus"));
        return snapshot;
    }

    private static ComputationCpuEntry cpu(int serial) {
        return new ComputationCpuEntry(serial, "CPU " + serial, null, BigInteger.ZERO, BigInteger.ZERO,
            0, 0, 1024, 4, CpuSelectionMode.ANY, Identifier.parse("neoecoae:cpu.png"), "idle");
    }

    private static void assertDrawn(com.lowdragmc.lowdraglib2.gui.ui.UIElement element,
            int serial) throws Exception {
        var client = cn.dancingsnow.neoecoae.gui.common.ClientTestSupport.withOptions(mock(Minecraft.class));
        set(client, "font", mock(Font.class));
        var graphics = mock(GuiGraphicsExtractor.class);
        when(graphics.pose()).thenReturn(new org.joml.Matrix3x2fStack(16));
        var context = mock(GUIContext.class);
        set(context, "graphics", graphics);
        var drawn = new ArrayList<Integer>();
        try (var minecraft = mockStatic(Minecraft.class);
             var rows = mockStatic(ComputationCpuSelector.class);
             var keys = mockStatic(AEKeyRendering.class);
             var aeConfig = mockStatic(AEConfig.class)) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            aeConfig.when(AEConfig::instance).thenReturn(mock(AEConfig.class));
            rows.when(() -> ComputationCpuSelector.drawCpuRow(any(), any(), any(), anyFloat(), anyFloat(),
                anyBoolean(), any(), any(), anyFloat())).thenAnswer(call -> {
                    drawn.add(((ComputationCpuEntry) call.getArgument(2)).serial());
                    return null;
                });
            // The concrete panels expose drawBackgroundAdditional publicly; UIElement's is protected.
            com.lowdragmc.lowdraglib2.gui.ui.UIElementRendererRegistry.findRenderer(element)
                .drawBackgroundAdditional(element, context);
            assertEquals(List.of(serial), drawn, "a pending page must keep drawing the last complete CPU rows");
            if (element instanceof ComputationCpuStatusPanel) {
                keys.verify(() -> AEKeyRendering.drawInGui(client, graphics, 0, 0, AEItemKey.of(Items.STONE)));
            }
        }
    }

    private static void scroll(Object target, String name, int offset) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        method.invoke(target, offset);
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
