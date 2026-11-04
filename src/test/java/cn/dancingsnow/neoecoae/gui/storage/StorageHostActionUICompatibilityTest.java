package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageHostActionUICompatibilityTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
            Class.forName(UIElement.class.getName());
        }
    }

    // The literal descriptor embedded in addons compiled against the old 13-component record.
    private static final String OLD_CONSTRUCTOR = "(Lnet/minecraft/world/entity/player/Player;"
        + "Ljava/util/function/IntSupplier;Ljava/util/function/BooleanSupplier;Ljava/util/function/Consumer;"
        + "Ljava/lang/Runnable;Ljava/lang/Runnable;Ljava/lang/Runnable;"
        + "Ljava/util/function/BooleanSupplier;Ljava/util/function/BooleanSupplier;Ljava/util/function/Supplier;"
        + "Ljava/util/function/IntSupplier;Ljava/util/function/IntConsumer;Ljava/util/function/IntConsumer;)V";

    @Test
    void oldBinaryConstructorStillLinksAndPreservesAllCallbacks() throws Throwable {
        var constructor = MethodHandles.publicLookup().findConstructor(StorageHostActionUI.Config.class,
            MethodType.fromMethodDescriptorString(OLD_CONSTRUCTOR, getClass().getClassLoader()));
        Player player = mock(Player.class);
        IntSupplier length = () -> 5;
        BooleanSupplier mirrored = () -> true;
        Consumer<Boolean> setMirrored = mock(Consumer.class);
        Runnable decrease = mock(Runnable.class);
        Runnable increase = mock(Runnable.class);
        Runnable build = mock(Runnable.class);
        BooleanSupplier formed = () -> false;
        BooleanSupplier building = () -> true;
        Supplier<MultiBlockPlacementPlan> plan = mock(Supplier.class);
        IntSupplier priority = () -> 42;
        IntConsumer setPriority = mock(IntConsumer.class);
        IntConsumer changePriority = mock(IntConsumer.class);
        var config = (StorageHostActionUI.Config) constructor.invoke(player, length, mirrored, setMirrored,
            decrease, increase, build, formed, building, plan, priority, setPriority, changePriority);
        assertSame(player, config.player());
        assertSame(length, config.selectedLength());
        assertSame(mirrored, config.mirrored());
        assertSame(setMirrored, config.setMirrored());
        assertSame(decrease, config.decreaseLength());
        assertSame(increase, config.increaseLength());
        assertSame(build, config.build());
        assertSame(formed, config.formed());
        assertSame(building, config.buildInProgress());
        assertSame(plan, config.previewPlan());
        assertSame(priority, config.priority());
        assertSame(setPriority, config.setPriority());
        assertSame(changePriority, config.changePriority());
        assertNotNull(config.definition());
        assertNull(config.definition().get());
    }

    @Test
    void oldAndNewConfigsForwardTheirDefinitionSupplierToTheBuilder() {
        var legacy = new StorageHostActionUI.Config(null, () -> 1, () -> false, ignored -> {},
            () -> {}, () -> {}, () -> {}, () -> false, () -> false, () -> null,
            () -> 0, ignored -> {}, ignored -> {});
        Supplier<MultiBlockDefinition> definition = mock(Supplier.class);
        var modern = new StorageHostActionUI.Config(null, legacy.selectedLength(), legacy.mirrored(),
            legacy.setMirrored(), legacy.decreaseLength(), legacy.increaseLength(), legacy.build(),
            legacy.formed(), legacy.buildInProgress(), legacy.previewPlan(), definition,
            legacy.priority(), legacy.setPriority(), legacy.changePriority());
        try (var builder = mockStatic(MultiblockBuilderUI.class); var priority = mockStatic(StoragePriorityUI.class)) {
            var buildWindow = mock(UIElement.class);
            var priorityWindow = mock(UIElement.class);
            builder.when(() -> MultiblockBuilderUI.createFloatingPanel(any())).thenReturn(buildWindow);
            priority.when(() -> StoragePriorityUI.createFloatingPanel(any())).thenReturn(priorityWindow);
            for (var config : new StorageHostActionUI.Config[] {legacy, modern}) {
                var elements = StorageHostActionUI.create(config);
                assertSame(buildWindow, elements.buildWindow());
                assertSame(priorityWindow, elements.priorityWindow());
                builder.verify(() -> MultiblockBuilderUI.createFloatingPanel(argThat(
                    forwarded -> forwarded.definition() == config.definition()
                        && forwarded.previewPlan() == config.previewPlan())));
            }
        }
    }
}
