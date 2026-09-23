package cn.dancingsnow.neoecoae.integration;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/** Resolves optional integration key types only when a registered cell is actually used. */
public final class ExternalCellKeyTypes {
    private ExternalCellKeyTypes() {
    }

    public static Supplier<AEKeyType> byId(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return () -> Objects.requireNonNull(AEKeyTypes.get(id), "Missing AE key type " + id);
    }
}
