package cn.dancingsnow.neoecoae.gui.common;

import appeng.client.gui.Icon;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Creates a host toolbar button that opens a GuideME page. */
public final class GuideButton {
    private GuideButton() {
    }

    public static Button create(Player player, String page) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(AETextures.icon(Icon.HELP));
        button.setOnServerClick(ignored -> GuidesCommon.openGuide(
                player, AppEng.makeId("guide"), PageAnchor.parse(page)))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(
                    ButtonToolTips.OpenGuide.text().withColor(-1),
                    ButtonToolTips.OpenGuideDetail.text().withStyle(ChatFormatting.GRAY)),
                null,
                null,
                null));
        return button;
    }
}
