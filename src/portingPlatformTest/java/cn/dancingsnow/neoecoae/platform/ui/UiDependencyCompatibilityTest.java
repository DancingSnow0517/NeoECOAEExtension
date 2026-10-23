package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.FluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.integration.xei.jei.ModularUIRecipeCategory;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UiDependencyCompatibilityTest {
    @BeforeAll
    static void bootstrap() { PlatformTestBootstrap.initialize(); }

    @Test
    void pointerStateIsSharedWithChildrenAndClearedWhenReleasedOutsideTheMenu() {
        var root = new UIElement().layout(layout -> layout.width(40).height(20));
        var child = new UIElement().layout(layout -> layout.width(20).height(20));
        root.addChild(child);
        new ModularUI(UI.of(root), mock(Player.class));
        root.arrange();
        assertFalse(root.isMouseDown(0));
        root.mouseClicked(5, 5, 0);
        assertTrue(root.isMouseDown(0));
        assertTrue(child.isMouseDown(0));
        assertFalse(child.isMouseDown(1));
        root.mouseReleased(100, 100, 0);
        assertFalse(root.isMouseDown(0));
        assertFalse(child.isMouseDown(0));
    }

    @Test
    void remoteFluidSlotReadsNativeInitialAndIncrementalSyncWithoutALocalTank() {
        var tank = new FluidTank(4_000);
        tank.setFluid(new FluidStack(Fluids.WATER, 1_500));
        var server = new FluidSlot().bind(tank, 0);
        var client = new FluidSlot() {
            @Override public boolean isRemote() { return true; }
        };
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            server.getWidgetsByType(TankWidget.class).get(0).writeInitialData(buffer);
            var clientTank = client.getWidgetsByType(TankWidget.class).get(0);
            clientTank.readInitialData(buffer);
            assertEquals(Fluids.WATER, client.getValue().getFluid());
            assertEquals(1_500, client.getValue().getAmount());
            buffer.clear();
            buffer.writeVarLong(2_500);
            clientTank.readUpdateInfo(3, buffer);
            assertEquals(2_500, client.getValue().getAmount());
            clientTank.readUpdateInfo(1, buffer);
            assertTrue(client.getValue().isEmpty());
            assertEquals("0 / 4000 mB", client.getFullTooltipTexts().get(1).getString());
        } finally {
            buffer.release();
        }
    }

    @Test
    void jeiExtrasAndTooltipsReuseOneNativeWrapperForAnEcoRecipeModel() throws ReflectiveOperationException {
        var wrapper = mock(ModularWrapper.class);
        var widget = new com.lowdragmc.lowdraglib.gui.widget.WidgetGroup(0, 0, 18, 18);
        var nativeUI = new com.lowdragmc.lowdraglib.gui.modular.ModularUI(widget,
            com.lowdragmc.lowdraglib.gui.modular.IUIHolder.EMPTY, mock(Player.class));
        // Avoid the wrapper constructor's dependency on a running Minecraft client.
        var nativeUIField = com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer.class.getField("modularUI");
        nativeUIField.setAccessible(true);
        nativeUIField.set(wrapper, nativeUI);
        when(wrapper.getWidget()).thenReturn(widget);
        wrapper.tooltipTexts = List.of(Component.literal("native tooltip"));
        var created = new AtomicInteger();
        var category = new ModularUIRecipeCategory<String>(ignored -> null) {
            @Override protected ModularWrapper<?> createWrapper(String recipe) {
                created.incrementAndGet();
                return wrapper;
            }
            @Override public RecipeType<String> getRecipeType() {
                return RecipeType.create("neoecoae", "compatibility", String.class);
            }
            @Override public Component getTitle() { return Component.empty(); }
            @Override public IDrawable getIcon() { return null; }
            @Override public int getWidth() { return 18; }
            @Override public int getHeight() { return 18; }
        };
        var extras = mock(IRecipeExtrasBuilder.class);
        var focuses = mock(IFocusGroup.class);
        category.createRecipeExtras(extras, "eco recipe", focuses);
        category.createRecipeExtras(extras, "eco recipe", focuses);
        var tooltip = mock(ITooltipBuilder.class);
        category.getTooltip(tooltip, "eco recipe", null, 0, 0);
        assertEquals(1, created.get());
        verify(extras, times(2)).addGuiEventListener(any());
        verify(extras, times(2)).addWidget(any());
        verify(tooltip).addAll(wrapper.tooltipTexts);
    }
}
