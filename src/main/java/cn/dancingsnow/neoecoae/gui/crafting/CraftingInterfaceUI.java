package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Supplier;

/** Compact control surface for moving compatible network patterns into ECO crafting buses. */
public final class CraftingInterfaceUI {
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;

    private CraftingInterfaceUI() {
    }

    public static ModularUI create(
            ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface,
            Player player) {
        UIElement root = new UIElement().layout(layout -> layout
                .width(224)
                .height(116)
                .paddingAll(8)
                .gapAll(6)
                .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .addClass("panel_bg");
        root.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.crafting_interface.title")));

        UIElement contentFrame = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .flex(1)
                .paddingAll(8)
                .gapAll(3)
                .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(style -> style.backgroundTexture(Sprites.BORDER_THICK_RT1));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
                .append(": ")
                .append(Component.translatable(craftingInterface.isTargetOnline()
                        ? "gui.neoecoae.storage_interface.connected"
                        : "gui.neoecoae.storage_interface.disconnected")
                        .withColor(craftingInterface.isTargetOnline() ? STATUS_CONNECTED : STATUS_DISCONNECTED))));
        contentFrame.addChild(transferButton(craftingInterface));
        contentFrame.addChild(statusLabel(craftingInterface::getPatternTransferPrimaryStatus));
        contentFrame.addChild(statusLabel(craftingInterface::getPatternTransferSecondaryStatus));
        root.addChild(contentFrame);

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), player);
    }

    private static Button transferButton(ECOMachineInterfaceBlockEntity<NECraftingCluster> craftingInterface) {
        Button button = new Button()
                .setText(Component.translatable("gui.neoecoae.host.crafting.pattern_transfer"))
                .setOnServerClick(event -> craftingInterface.transferNetworkPatterns());
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.layout(layout -> layout.widthPercent(100).height(18));
        return button;
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.layout(layout -> layout.height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.height(12).marginLeft(2));
        return label;
    }
}
