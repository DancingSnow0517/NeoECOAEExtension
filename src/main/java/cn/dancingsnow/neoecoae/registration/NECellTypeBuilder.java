package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.AbstractBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;

public class NECellTypeBuilder<P> extends AbstractBuilder<ECOCellType, ECOCellType, P, NECellTypeBuilder<P>> {
    private final ECOCellType.Builder builder = ECOCellType.builder();

    public static <P> NECellTypeBuilder<P> create(
            AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback) {
        return new NECellTypeBuilder<>(owner, parent, name, callback);
    }

    protected NECellTypeBuilder(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback) {
        super(owner, parent, name, callback, NERegistries.Keys.CELL_TYPE);
    }

    public NECellTypeBuilder<P> desc(Component desc) {
        builder.desc(desc);
        return this;
    }

    public NECellTypeBuilder<P> typeCount(int count) {
        builder.typeCount(count);
        return this;
    }

    @Override
    public NECellTypeEntry register() {
        return (NECellTypeEntry) super.register();
    }

    @Override
    protected RegistryEntry<ECOCellType> createEntryWrapper(RegistryObject<ECOCellType> delegate) {
        return new NECellTypeEntry(getOwner(), delegate);
    }

    @Override
    protected ECOCellType createEntry() {
        builder.id(ResourceLocation.fromNamespaceAndPath(getOwner().getModid(), getName()));
        return builder.build();
    }
}
