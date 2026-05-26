package com.lowdragmc.lowdraglib2.gui.ui.style;

import net.minecraft.resources.ResourceLocation;

public class StylesheetManager {
    public static final String PATH = "ldlib2/stylesheets";
    public static final StylesheetManager INSTANCE = new StylesheetManager();

    public Object getStylesheetSafe(ResourceLocation location) {
        return location;
    }
}
