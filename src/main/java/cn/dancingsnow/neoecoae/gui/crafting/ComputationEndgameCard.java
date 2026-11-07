package cn.dancingsnow.neoecoae.gui.crafting;

import static cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerInteractions.statusIntArg;
import static cn.dancingsnow.neoecoae.gui.crafting.CraftingControllerInteractions.statusReasons;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Card view of the computation host's infinite-state checklist. The server syncs either the
 * {@code endgame.active} line or the {@code endgame.title} line whose siblings are the unmet
 * {@code endgame.reason.*} entries; the client rebuilds the checklist from those keys.
 */
final class ComputationEndgameCard {
    private static final String ENDGAME = "gui.neoecoae.computation.endgame.";
    private static final String TITLE_KEY = ENDGAME + "title";
    private static final String ACTIVE_KEY = ENDGAME + "active";
    private static final String REASON_PREFIX = ENDGAME + "reason.";
    private static final String CARD = "gui.neoecoae.computation.ui.endgame_card.";
    private static final String SHARED = "gui.neoecoae.crafting.ui.virtual_card.";
    private static final int HOST_LIMIT = 8;

    private ComputationEndgameCard() {}

    static boolean applies(Component status) {
        return status != null && status.getContents() instanceof TranslatableContents contents
            && (TITLE_KEY.equals(contents.getKey()) || ACTIVE_KEY.equals(contents.getKey()));
    }

    static List<Object> create(Component status) {
        Map<String, Object[]> reasons = statusReasons(status, REASON_PREFIX);
        int unmet = reasons.size();
        Object[] hostArgs = reasons.get("host_count");
        int hosts = hostArgs == null ? HOST_LIMIT : statusIntArg(hostArgs, 0, 0);
        int limit = hostArgs == null ? HOST_LIMIT : Math.max(1, statusIntArg(hostArgs, 1, HOST_LIMIT));

        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.DOTS, Component.translatable(SHARED + "title"),
                unmet == 0 ? Component.translatable(SHARED + "met") : Component.translatable(SHARED + "unmet", unmet),
                unmet == 0 ? CraftingTooltipCard.OK : CraftingListCard.DANGER)
            .divider()
            .checkRow(hostArgs == null, Component.translatable(CARD + "hosts"), Component.literal(hosts + " / " + limit));
        condition(card, reasons, "tier", CARD + "tier");
        condition(card, reasons, "max_length", SHARED + "max_length");
        condition(card, reasons, "high_energy_switch", SHARED + "high_energy_switch");
        condition(card, reasons, "drives", CARD + "drives");
        return card.build();
    }

    private static void condition(CraftingListCard.Builder card, Map<String, Object[]> reasons, String reason, String key) {
        boolean ok = !reasons.containsKey(reason);
        card.checkRow(ok, Component.translatable(key));
        if (!ok) card.note(Component.translatable(key + ".missing"), CraftingListCard.DANGER);
    }

}
