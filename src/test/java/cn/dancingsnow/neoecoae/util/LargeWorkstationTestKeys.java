package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/** Shared registry because AEKeyType's lazy codec retains its first registry. */
public final class LargeWorkstationTestKeys {
    public static final AEKeyType DATA_TYPE = (AEKeyType) upstream("DigitalizationKeyType", "TYPE");
    public static final AEKey DATA_FLOW = (AEKey) upstream("DataFlowKey", "INSTANCE");
    public static final AEKeyType MANIFEST_TYPE = (AEKeyType) upstream("ManifestBinaryKeyType", "TYPE");
    public static final AEKey MANIFEST_DATA = (AEKey) upstream("DataKey", "INSTANCE");
    public static final Registry<AEKeyType> REGISTRY = create();

    private LargeWorkstationTestKeys() {}

    private static Registry<AEKeyType> create() {
        var registry = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        Registry.register(registry, LightningKeyType.INSTANCE.getId(), LightningKeyType.INSTANCE);
        Registry.register(registry, DATA_TYPE.getId(), DATA_TYPE);
        Registry.register(registry, MANIFEST_TYPE.getId(), MANIFEST_TYPE);
        return registry;
    }

    private static Object upstream(String name, String field) {
        try {
            return Class.forName("com.fish_dan_.data_energistics.ae2.key." + name).getField(field).get(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot load released Data Energistics key codec", failure);
        }
    }
}
