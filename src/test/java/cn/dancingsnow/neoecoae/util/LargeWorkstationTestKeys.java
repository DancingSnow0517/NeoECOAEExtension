package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEKeyType;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/** Shared registry because AEKeyType's lazy codec retains its first registry. */
public final class LargeWorkstationTestKeys {
    public static final Registry<AEKeyType> REGISTRY = create();

    private LargeWorkstationTestKeys() {}

    private static Registry<AEKeyType> create() {
        var registry = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        Registry.register(registry, LightningKeyType.INSTANCE.getId(), LightningKeyType.INSTANCE);
        return registry;
    }
}
