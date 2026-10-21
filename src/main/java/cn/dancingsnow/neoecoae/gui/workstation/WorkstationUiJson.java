package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

/** Shared resource loader for workstation UI roots. Dynamic machine elements stay in contexts. */
final class WorkstationUiJson {
    private WorkstationUiJson() {}

    static UIElement load(ResourceLocation location, NeoUiJsonContext context) {
        return NeoUiJsonLoader.load(location, context);
    }
}
