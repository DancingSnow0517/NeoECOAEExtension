package com.lowdragmc.lowdraglib2.gui.texture;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;

public class UIResourceTexture implements IGuiTexture {
    private final BuiltinPath path;

    public UIResourceTexture(BuiltinPath path) {
        this.path = path;
    }

    public BuiltinPath path() {
        return path;
    }
}
