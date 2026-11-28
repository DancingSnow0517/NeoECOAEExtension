package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.grid.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingTooltipCard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Persistent button-local priority state; rebuilding a hover card preserves pending clicks. */
final class LargeWorkstationPowerPriorityCard {
    private static final String PREFIX = "gui.neoecoae.workstation.power_priority.";
    private static final int AE = 0xFF73DFED;
    private static final int FE = 0xFFE6BB68;
    private boolean fePreferred;
    private long clicks;

    LargeWorkstationPowerPriorityCard(boolean fePreferred) {
        this.fePreferred = fePreferred;
    }

    void click() {
        clicks++;
        fePreferred = !fePreferred;
    }

    void receive(CompoundTag state) {
        // Acknowledgements and priority travel together. Older replies must retain pending local clicks.
        long pending = clicks - state.getLong("acknowledgedClicks");
        fePreferred = state.getBoolean("fePreferred") ^ ((pending & 1) != 0);
    }

    boolean fePreferred() { return fePreferred; }

    CraftingListCard.Builder builder(CompoundTag state) {
        return CraftingListCard.builder().minimumWidth(240)
            .header(CraftingListCard.Glyph.BOLT, tr("title"), card("energy_monitor"), CraftingTooltipCard.MUTED)
            .space(3)
            .row(null, FE, tr("peak"), amount(state, "energyPeak", "ae_rate"), FE)
            .space(5)
            .row(null, FE, tr("network_fe"), amount(state, "networkFE", "fe_amount"), FE)
            .space(5)
            .row(null, AE, tr("network_ae"), amount(state, "networkAE", "ae_amount"), AE)
            .space(5)
            .row(null, CraftingTooltipCard.MUTED, tr("title"),
                Component.literal(fePreferred ? "FE → AE" : "AE → FE"), fePreferred ? FE : AE);
    }

    private static Component amount(CompoundTag state, String key, String unit) {
        if (!state.contains(key)) return Component.literal("—");
        return card(unit, "networkFE".equals(key)
            ? DisplayNumbers.compact(state.getLong(key)) : DisplayNumbers.compact(state.getDouble(key)));
    }

    private static Component tr(String key) { return Component.translatable(PREFIX + key); }
    private static Component card(String key, Object... args) {
        return Component.translatable("gui.neoecoae.workstation.card." + key, args);
    }
}
