package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Retained UI content measured and drawn inside Minecraft's normal tooltip frame. */
public record ModularUITooltipComponent(UIElement content) implements TooltipComponent {}
