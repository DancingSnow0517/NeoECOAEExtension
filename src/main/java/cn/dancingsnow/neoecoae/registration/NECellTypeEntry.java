package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraftforge.registries.RegistryObject;

public class NECellTypeEntry extends RegistryEntry<ECOCellType> {
    public NECellTypeEntry(AbstractRegistrate<?> owner, RegistryObject<ECOCellType> delegate) {
        super(owner, delegate);
    }
}
