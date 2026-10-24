package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.UiTestSupport;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiBlockInfoWrapperTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class); var registrate = mockStatic(NERegistrate.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(UIElement.class.getName());
            Class.forName(NETextures.class.getName());
        }
    }

    @Test
    void lengthClicksWrapBothWaysAndUpdateExistingMaterialSlots() {
        withPreview(fixture -> {
            var wrapper = fixture.wrapper();
            Button length = field(wrapper, "expandButton");
            List<ItemSlot> slots = field(wrapper, "requiredItemSlots");
            ItemSlot stone = slots.get(0);
            assertEquals(1, stone.getValue().getCount());

            click(length, 1);
            assertEquals(3, fixture.length().get());
            assertEquals(3, stone.getValue().getCount());
            click(length, 0);
            assertEquals(1, fixture.length().get());
            click(length, 0);
            assertEquals(2, fixture.length().get());
            assertEquals(2, stone.getValue().getCount());
            assertSame(stone, ((List<?>) field(wrapper, "requiredItemSlots")).get(0));
        });
    }

    @Test
    void layersWrapThroughAllLayersAndChangingThemClearsFormedMode() {
        withPreview(fixture -> {
            var wrapper = fixture.wrapper();
            Button layers = field(wrapper, "layerButton");
            Button formed = field(wrapper, "formedButton");
            click(layers, 1);
            assertEquals(1, (int) field(wrapper, "layer"));
            click(layers, 0);
            assertEquals(-1, (int) field(wrapper, "layer"));
            click(layers, 0);
            assertEquals(0, (int) field(wrapper, "layer"));
            click(formed, 0);
            assertTrue((boolean) field(wrapper, "formed"));
            assertSame(NETextures.BUTTON_HIGHLIGHTED, formed.getButtonStyle().baseTexture());
            click(layers, 0);
            assertFalse((boolean) field(wrapper, "formed"));
            assertSame(NETextures.BUTTON, formed.getButtonStyle().baseTexture());
        });
    }

    @Test
    void controlsAreOutsideTheSceneAndTheSelectionSlotStartsHidden() {
        withPreview(fixture -> {
            var wrapper = fixture.wrapper();
            UIElement scene = field(wrapper, "scene");
            UIElement selected = field(wrapper, "selectedSlot");
            Button length = field(wrapper, "expandButton");
            assertNotSame(scene.getParentElement(), length.getParentElement());
            assertSame(scene.getParentElement(), selected.getParentElement());
            assertFalse(selected.isDisplayed());
            assertEquals(4, fixture.root().getChildren().size());
            assertEquals(10F, fixture.root().getChildren().get(0).getLayoutStyle().height);
            assertEquals(16F, length.getParentElement().getLayoutStyle().height);
            assertEquals(27F, fixture.root().getChildren().get(3).getLayoutStyle().height);
        });
    }

    private static void withPreview(Consumer<Fixture> test) {
        try (var ldlib = mockStatic(LDLib.class); var minecraft = mockStatic(Minecraft.class);
             var worlds = mockConstruction(TrackedDummyWorld.class);
             var scenes = mockConstruction(SceneWidget.class)) {
            ldlib.when(LDLib::isRemote).thenReturn(true);
            var client = mock(Minecraft.class);
            when(client.getSoundManager()).thenReturn(mock(SoundManager.class));
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            AtomicInteger length = new AtomicInteger();
            var definition = MultiBlockDefinition.builder(Holder.direct(Blocks.STONE))
                .setBlockRepeatable(BlockPos.ZERO, Direction.EAST, Blocks.STONE.defaultBlockState())
                .setBlock(new BlockPos(0, 1, 0), Blocks.GLASS.defaultBlockState())
                .expandMax(3).create(ignored -> {});
            var wrapper = new MultiBlockInfoWrapper(definition, 170, 190, length::set);
            var ui = wrapper.createModularUI();
            test.accept(new Fixture(wrapper, ui.getRootElement(), length));
        }
    }

    private static void click(Button button, int mouseButton) {
        button.applyBounds(0, 0, 50, 16);
        assertTrue(button.mouseClicked(2, 2, mouseButton));
        assertTrue(button.mouseReleased(2, 2, mouseButton));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        return (T) UiTestSupport.get(owner, name);
    }

    private record Fixture(MultiBlockInfoWrapper wrapper, UIElement root, AtomicInteger length) {}
}
