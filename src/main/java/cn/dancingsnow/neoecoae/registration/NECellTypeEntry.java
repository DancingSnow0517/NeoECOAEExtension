package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.neoforged.neoforge.registries.DeferredHolder;

public class NECellTypeEntry extends RegistryEntry<ECOCellType, ECOCellType> {
    public NECellTypeEntry(AbstractRegistrate<?> owner, DeferredHolder<ECOCellType, ECOCellType> key) {
        super(owner, key);
    }
}
