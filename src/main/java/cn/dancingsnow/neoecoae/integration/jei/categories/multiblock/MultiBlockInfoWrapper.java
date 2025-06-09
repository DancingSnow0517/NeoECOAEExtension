package cn.dancingsnow.neoecoae.integration.jei.categories.multiblock;

import cn.dancingsnow.neoecoae.gui.widget.MultiBlockPreviewWidget;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import lombok.Getter;

public class MultiBlockInfoWrapper extends ModularWrapper<MultiBlockPreviewWidget> {
    @Getter
    private final MultiBlockDefinition def;
    public MultiBlockInfoWrapper(MultiBlockDefinition def) {
        super(new MultiBlockPreviewWidget(def));
        this.def = def;
    }
}
