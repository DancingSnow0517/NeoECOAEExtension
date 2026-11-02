package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageHostProgressAnimationTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        StorageHostUiTest.bootstrap();
    }

    @Test
    @SuppressWarnings("unchecked")
    void capacityAndEnergyAnimateToTheirActualValuesAndReplayAfterRemoval() {
        AtomicLong now = new AtomicLong();
        try (var ldlib = mockStatic(LDLib2.class); var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            clock.when(Util::getMillis).thenAnswer(call -> now.get());
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            BindableValue<Tag> synced = (BindableValue<Tag>) root.getChildren().getFirst();
            synced.setValue(state(false, false).toTag());
            UIElement capacity = bar(root, "capacity_bar", 208, 313, 160, 8);
            UIElement energy = bar(root, "energy_bar", 373, 292, 74, 7);
            GUIContext context = mock(GUIContext.class);
            capacity.drawBackgroundAdditional(context);
            energy.drawBackgroundAdditional(context);
            verifyNoInteractions(context);

            now.set(250);
            capacity.drawBackgroundAdditional(context);
            energy.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62),
                eq(208F), eq(313F), eq(60F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 133 && sprite.spriteSize.width == 9),
                eq(373F), eq(292F), eq(9F), eq(7F));
            verifyNoMoreInteractions(context);

            clearInvocations(context);
            now.set(375);
            capacity.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62),
                eq(208F), eq(313F), eq(90F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 24 && sprite.spritePosition.y == 62),
                eq(298F), eq(313F), eq(11F), eq(8F));
            verifyNoMoreInteractions(context);

            clearInvocations(context);
            now.set(500);
            capacity.drawBackgroundAdditional(context);
            energy.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62),
                eq(208F), eq(313F), eq(90F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 24 && sprite.spritePosition.y == 62),
                eq(298F), eq(313F), eq(30F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.y == 133 && sprite.spriteSize.width == 19),
                eq(373F), eq(292F), eq(19F), eq(7F));
            verifyNoMoreInteractions(context);

            UIElement parent = new UIElement().addChild(capacity);
            parent.removeChild(capacity);
            parent.addChild(capacity);
            clearInvocations(context);
            capacity.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
            now.set(1_000);
            capacity.drawBackgroundAdditional(context);
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 32 && sprite.spritePosition.y == 62),
                eq(208F), eq(313F), eq(90F), eq(8F));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.spritePosition.x == 24 && sprite.spritePosition.y == 62),
                eq(298F), eq(313F), eq(30F), eq(8F));
            verifyNoMoreInteractions(context);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void resourceRevealPreservesSegmentBoundariesAndOnlyHoversTheVisibleArea() {
        AtomicLong now = new AtomicLong();
        try (var ldlib = mockStatic(LDLib2.class); var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            clock.when(Util::getMillis).thenAnswer(call -> now.get());
            UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
            ((BindableValue<Tag>) root.getChildren().getFirst()).setValue(state(false, false).toTag());
            UIElement resources = bar(root, "resource_bar", 208, 334, 160, 8);
            doReturn(true).when(resources).isHover();
            GUIContext context = mock(GUIContext.class);
            context.mouseX = 220;
            context.mouseY = 337;
            resources.drawBackgroundAdditional(context);
            verifyNoInteractions(context);
            assertTrue(resources.collectHoverTooltips().tooltipTexts().isEmpty());

            now.set(250);
            resources.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 208F, 334F, 80F, 8F);
            clearInvocations(context);
            context.mouseX = 330;
            resources.drawBackgroundAdditional(context);
            verify(context, never()).drawTexture(eq(StoragePanelTextures.RESOURCE_HOVER),
                anyFloat(), anyFloat(), anyFloat(), anyFloat());
            assertTrue(resources.collectHoverTooltips().tooltipTexts().isEmpty());

            clearInvocations(context);
            now.set(375);
            resources.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 328F, 334F, 15F, 8F);
            var line = resources.collectHoverTooltips().tooltipTexts().getFirst();
            var arguments = assertInstanceOf(TranslatableContents.class, line.getContents()).getArgs();
            assertEquals("25.0%", arguments[2]);

            clearInvocations(context);
            now.set(500);
            resources.drawBackgroundAdditional(context);
            verify(context).drawTexture(StoragePanelTextures.RESOURCE_HOVER, 328F, 334F, 40F, 8F);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void infiniteAndMigratingBarsRevealWithoutStretchingTheirPattern() {
        AtomicLong now = new AtomicLong();
        try (var ldlib = mockStatic(LDLib2.class); var clock = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            clock.when(Util::getMillis).thenAnswer(call -> now.get());
            for (boolean migrating : List.of(false, true)) {
                now.set(0);
                UIElement root = StorageHostUI.create(StoragePanelStateTest.config(List.of(), false));
                ((BindableValue<Tag>) root.getChildren().getFirst()).setValue(state(!migrating, migrating).toTag());
                UIElement capacity = bar(root, "capacity_bar", 208, 313, 160, 8);
                GUIContext context = mock(GUIContext.class);
                capacity.drawBackgroundAdditional(context);
                verifyNoInteractions(context);
                now.set(250);
                capacity.drawBackgroundAdditional(context);
                verify(context).enableScissor(208F, 313F, 80F, 8F);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_FILL, 208F, 313F, 72F, 8F);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_SYMBOL, 280F, 313F, 16F, 8F);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_FILL, 296F, 313F, 72F, 8F);
                verify(context).disableScissor();
                verifyNoMoreInteractions(context);
                clearInvocations(context);
                now.set(500);
                capacity.drawBackgroundAdditional(context);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_FILL, 208F, 313F, 72F, 8F);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_SYMBOL, 280F, 313F, 16F, 8F);
                verify(context).drawTexture(StoragePanelTextures.INFINITE_CAPACITY_FILL, 296F, 313F, 72F, 8F);
                verifyNoMoreInteractions(context);
            }
        }
    }

    private static UIElement bar(UIElement root, String id, float x, float y, float width, float height) {
        UIElement element = spy(root.selectId(id).findFirst().orElseThrow());
        doReturn(x).when(element).getPositionX();
        doReturn(y).when(element).getPositionY();
        doReturn(width).when(element).getSizeWidth();
        doReturn(height).when(element).getSizeHeight();
        return element;
    }

    private static StoragePanelState state(boolean infinite, boolean migrating) {
        var items = new StoragePanelState.TypeLine(2, Component.literal("items"), 0xEC8600,
            1, 10, 90, 100, "90", 2, BigInteger.valueOf(90));
        var fluids = new StoragePanelState.TypeLine(1, Component.literal("fluids"), 0x3894FF,
            1, 10, 30, 60, "30", 1, BigInteger.valueOf(30));
        return new StoragePanelState(Component.empty(), 2, true, infinite, migrating,
            25, 100, 0, "120", 0.75, 0, 0, List.of(), List.of(items, fluids), "", 0, false);
    }
}
