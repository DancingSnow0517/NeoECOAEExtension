package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.jetbrains.annotations.Nullable;

/**
 * Shared registry because AEKeyType's lazy codec retains its first registry.
 *
 * <p>26.1 port: the Data Energistics and AE2 Lightning Tech key types only exist as 1.21.1 jars, which cannot
 * be initialized on 26.1 ({@code ResourceLocation} is gone). Their fields are {@code null} unless the classes
 * load, and the registry then only holds AE2's item and fluid key types.</p>
 */
public final class LargeWorkstationTestKeys {
    public static final @Nullable AEKeyType DATA_TYPE = (AEKeyType) upstream("DigitalizationKeyType", "TYPE");
    public static final @Nullable AEKey DATA_FLOW = (AEKey) upstream("DataFlowKey", "INSTANCE");
    public static final @Nullable AEKeyType MANIFEST_TYPE = (AEKeyType) upstream("ManifestBinaryKeyType", "TYPE");
    public static final @Nullable AEKey MANIFEST_DATA = (AEKey) upstream("DataKey", "INSTANCE");
    public static final Registry<AEKeyType> REGISTRY = create();

    private LargeWorkstationTestKeys() {}

    /** Whether the released Data Energistics key classes could be loaded in this environment. */
    public static boolean dataEnergisticsAvailable() {
        return DATA_TYPE != null && DATA_FLOW != null && MANIFEST_TYPE != null && MANIFEST_DATA != null;
    }

    private static Registry<AEKeyType> create() {
        var registry = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        var lightning = lightningKeyType();
        if (lightning != null) {
            Registry.register(registry, lightning.getId(), lightning);
        }
        if (dataEnergisticsAvailable()) {
            Registry.register(registry, DATA_TYPE.getId(), DATA_TYPE);
            Registry.register(registry, MANIFEST_TYPE.getId(), MANIFEST_TYPE);
        }
        return registry;
    }

    private static @Nullable AEKeyType lightningKeyType() {
        try {
            return (AEKeyType) Class.forName("com.moakiee.ae2lt.me.key.LightningKeyType").getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static @Nullable Object upstream(String name, String field) {
        try {
            return Class.forName("com.fish_dan_.data_energistics.integration.ae.ae2.key." + name).getField(field).get(null);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot load released Data Energistics key codec", failure);
        }
    }
}
