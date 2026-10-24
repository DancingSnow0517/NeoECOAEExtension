package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import java.util.List;

final class NEMegaBaseItems {
    private NEMegaBaseItems() {
    }

    static List<ItemEntry<? extends ECOStorageCellItem>> cells() {
        return List.of();
    }

    static void register() {
        // Intentional class-initialization barrier: no L4/L6 base MEGA cells are registered.
    }
}
