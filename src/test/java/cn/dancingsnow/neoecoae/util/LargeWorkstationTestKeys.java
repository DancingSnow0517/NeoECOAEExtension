package cn.dancingsnow.neoecoae.util;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/** Shared registry because AEKeyType's lazy codec retains its first registry. */
public final class LargeWorkstationTestKeys {
    public static final net.minecraftforge.registries.ForgeRegistry<AEKeyType> REGISTRY = create();

    private LargeWorkstationTestKeys() {}

    @SuppressWarnings("unchecked")
    private static net.minecraftforge.registries.ForgeRegistry<AEKeyType> create() {
        try {
            var builder = new net.minecraftforge.registries.RegistryBuilder<AEKeyType>()
                .setName(new net.minecraft.resources.ResourceLocation("neoecoae_sync_test", "key_types"));
            var create = net.minecraftforge.registries.RegistryBuilder.class.getDeclaredMethod("create");
            create.setAccessible(true);
            var registry = (net.minecraftforge.registries.ForgeRegistry<AEKeyType>) create.invoke(builder);
            registry.register(AEKeyType.items().getId(), AEKeyType.items());
            registry.register(AEKeyType.fluids().getId(), AEKeyType.fluids());
            registry.register(LightningKeyType.INSTANCE.getId(), LightningKeyType.INSTANCE);
            return registry;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize target AE key registry", failure);
        }
    }
}
