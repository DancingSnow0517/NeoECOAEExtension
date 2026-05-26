package com.lowdragmc.lowdraglib2.gui.ui.event;

public class UIEvents {
    public static final Object HOVER_TOOLTIPS = new Object();

    public static class HoverTooltipEvent {
        public Object currentElement;
        public HoverTooltips hoverTooltips;
    }
}
