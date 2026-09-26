package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
/** Lazily registers each channel type only when that channel's integration is present. */
final class NEMegaExternalCellTypes {
    private NEMegaExternalCellTypes() {
    }

    static Supplier<ECOCellType> air() {
        return Air.TYPE;
    }

    static Supplier<ECOCellType> experience() {
        return Experience.TYPE;
    }

    static Supplier<ECOCellType> soul() {
        return Soul.TYPE;
    }

    static Supplier<ECOCellType> mana() {
        return Mana.TYPE;
    }

    static Supplier<ECOCellType> source() {
        return Source.TYPE;
    }

    private static NECellTypeEntry register(String id, String name, int color) {
        REGISTRATE.addLang("cell_type", NeoECOAE.id(id), name);
        return REGISTRATE.cellType(id)
            .desc(Component.translatable("cell_type.neoecoae." + id).withColor(color))
            .typeCount(1)
            .register();
    }

    private static final class Air {
        private static final NECellTypeEntry TYPE = register("mega_air", "MEGA Air", 0x61c8e8);
    }

    private static final class Experience {
        private static final NECellTypeEntry TYPE = register("mega_experience", "MEGA Experience", 0x74d56d);
    }

    private static final class Soul {
        private static final NECellTypeEntry TYPE = register("mega_soul", "MEGA Soul", 0xa56acb);
    }

    private static final class Mana {
        private static final NECellTypeEntry TYPE = register("mega_mana", "MEGA Mana", 0x50c878);
    }

    private static final class Source {
        private static final NECellTypeEntry TYPE = register("mega_source", "MEGA Source", 0x9d60d1);
    }
}
