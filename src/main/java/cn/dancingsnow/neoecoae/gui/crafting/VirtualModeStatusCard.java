package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Card view of the infinite-mode checklist. The server keeps syncing the checklist as a translatable title
 * whose siblings are the unmet {@code virtual_reason.*} entries; the client rebuilds the card from those keys.
 */
final class VirtualModeStatusCard {
    static final String TITLE_KEY = "gui.neoecoae.crafting.virtual_status.title";
    private static final String REASON_PREFIX = "gui.neoecoae.crafting.virtual_reason.";
    private static final String CARD = "gui.neoecoae.crafting.ui.virtual_card.";

    private VirtualModeStatusCard() {}

    static boolean applies(Component status) {
        return status != null && status.getContents() instanceof TranslatableContents contents
            && TITLE_KEY.equals(contents.getKey());
    }

    static List<Object> create(Component status) {
        Map<String, Object[]> reasons = reasons(status);
        int hostLimit = NEFrequencyAllocator.HOST_LIMIT;
        Object[] hostArgs = reasons.get("host_count");
        int hosts = hostArgs == null ? hostLimit : intArg(hostArgs, 0, 0);
        int limit = hostArgs == null ? hostLimit : Math.max(1, intArg(hostArgs, 1, hostLimit));
        boolean hostsOk = hostArgs == null;
        int unmet = reasons.size();

        var card = CraftingListCard.builder()
            .header(CraftingListCard.Glyph.DOTS, Component.translatable(CARD + "title"),
                unmet == 0 ? Component.translatable(CARD + "met") : Component.translatable(CARD + "unmet", unmet),
                unmet == 0 ? CraftingTooltipCard.OK : CraftingListCard.DANGER)
            .divider()
            .section(Component.translatable(CARD + "features"));
        featureRow(card, CraftingListCard.Glyph.BOLT, "overclock", !reasons.containsKey("overclock"));
        featureRow(card, CraftingListCard.Glyph.SNOW, "cooling", !reasons.containsKey("cooling"));
        Object[] coolant = reasons.get("coolant_type");
        card.statusRow(CraftingListCard.Glyph.DROP, Component.translatable(CARD + "coolant"), coolant == null,
            Component.empty());
        if (coolant != null) {
            card.note(Component.translatable(CARD + "requires", componentArg(coolant, 0)
                .copy().withStyle(style -> style.withColor(CraftingTooltipCard.CYAN))), CraftingTooltipCard.MUTED);
        }

        card.divider()
            .section(Component.translatable(CARD + "network"))
            .row(CraftingListCard.Glyph.NODES, CraftingTooltipCard.MUTED, Component.translatable(CARD + "hosts"),
                Component.literal(hosts + " / " + limit), hostsOk ? CraftingTooltipCard.OK : CraftingListCard.DANGER)
            .bar(hosts / (double) limit, hostsOk ? CraftingTooltipCard.OK : CraftingTooltipCard.WARN);
        structureRow(card, "f9", reasons);
        structureRow(card, "high_energy_switch", reasons);
        structureRow(card, "max_length", reasons);
        if (reasons.containsKey("topology")) {
            card.checkRow(false, Component.translatable(REASON_PREFIX + "topology"));
        }
        return card.build();
    }

    private static void featureRow(CraftingListCard.Builder card, CraftingListCard.Glyph glyph, String key, boolean ok) {
        card.statusRow(glyph, Component.translatable(CARD + key), ok,
            Component.translatable(CARD + (ok ? "enabled" : "disabled")));
    }

    private static void structureRow(CraftingListCard.Builder card, String key, Map<String, Object[]> reasons) {
        boolean ok = !reasons.containsKey(key);
        card.checkRow(ok, Component.translatable(CARD + key));
        if (!ok) card.note(Component.translatable(CARD + key + ".missing"), CraftingListCard.DANGER);
    }

    private static Map<String, Object[]> reasons(Component status) {
        Map<String, Object[]> reasons = new LinkedHashMap<>();
        for (Component sibling : status.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents
                && contents.getKey().startsWith(REASON_PREFIX)) {
                reasons.put(contents.getKey().substring(REASON_PREFIX.length()), contents.getArgs());
            }
        }
        return reasons;
    }

    private static int intArg(Object[] args, int index, int fallback) {
        if (index >= args.length) return fallback;
        Object arg = args[index];
        if (arg instanceof Number number) return number.intValue();
        String text = arg instanceof Component component ? component.getString() : String.valueOf(arg);
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Component componentArg(Object[] args, int index) {
        if (index >= args.length) return Component.empty();
        return args[index] instanceof Component component ? component : Component.literal(String.valueOf(args[index]));
    }
}
