package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.AbstractBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredHolder;

public class NECellTypeBuilder<P> extends AbstractBuilder<ECOCellType, ECOCellType, P, NECellTypeBuilder<P>> {

    private final ECOCellType.Builder builder = ECOCellType.builder();

    public static <P> NECellTypeBuilder<P> create(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback) {
        return new NECellTypeBuilder<>(owner, parent, name, callback);
    }

    public NECellTypeBuilder(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback) {
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
    protected NECellTypeEntry createEntryWrapper(DeferredHolder<ECOCellType, ECOCellType> delegate) {
        return new NECellTypeEntry(getOwner(), delegate);
    }

    @Override
    public NECellTypeEntry register() {
        return (NECellTypeEntry) super.register();
    }

    @Override
    protected ECOCellType createEntry() {
        return builder.build();
    }
}
