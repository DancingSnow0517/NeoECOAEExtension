package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.core.AEConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
            .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
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
        assertEquals(6, field(panel, "cpuPageOffset"));
        assertEquals(6, field(panel, "itemPageOffset"));
        assertDrawn(panel, 7);
    }

    @Test void selectingAnotherCpuClearsAnUnfinishedItemScroll() throws Exception {
        var panel = new ComputationCpuStatusPanel(config(), new CpuSelectionState.Identities<>());
        panel.setValue(snapshot(0, 0, 1), false);
        scroll(panel, "scrollItemsTo", 6);
        panel.open(2);
        panel.setValue(snapshot(0, 0, 2), false);
        assertEquals(0, field(panel, "itemOffset"));
        assertFalse((boolean) field(panel, "itemScrollPending"));
    }

    @Test void sidebarKeepsItsPageVisibleUntilTheLatestScrollReply() throws Exception {
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

    @Test void returningToTheOriginalOffsetCannotAcknowledgeAnOldSnapshot() throws Exception {
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
            () -> net.minecraft.network.chat.Component.empty());
    }

    private static CompoundTag snapshot(int cpuOffset, int itemOffset, int serial) {
        return snapshot(cpuOffset, itemOffset, serial, 0, 0);
    }

    private static CompoundTag snapshot(int cpuOffset, int itemOffset, int serial,
            long cpuSequence, long itemSequence) {
        var snapshot = new CompoundTag();
        snapshot.putInt("total", 30);
        snapshot.putInt("cpuOffset", cpuOffset);
        snapshot.putInt("selectedSerial", serial);
        snapshot.putInt("itemTotal", 60);
        snapshot.putInt("itemOffset", itemOffset);
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
        snapshot.put("page", snapshot.getList("cpus", net.minecraft.nbt.Tag.TAG_COMPOUND));
        return snapshot;
    }

    private static ComputationCpuEntry cpu(int serial) {
        return new ComputationCpuEntry(serial, "CPU " + serial, null, BigInteger.ZERO, BigInteger.ZERO,
            0, 0, 1024, 4, CpuSelectionMode.ANY, ResourceLocation.parse("neoecoae:cpu.png"), "idle");
    }

    private static void assertDrawn(com.lowdragmc.lowdraglib2.gui.ui.UIElement element,
            int serial) throws Exception {
        var client = mock(Minecraft.class);
        set(client, "font", mock(Font.class));
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new PoseStack());
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
            element.drawBackgroundAdditional(context);
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
