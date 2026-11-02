package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;

/**
 * 26.1 port: key-type registry for GUI tests. The shared {@code util.LargeWorkstationTestKeys} also registers
 * Data Energistics / AE2 Lightning Tech key types, whose (1.21.1-only) classes cannot load on 26.1; the GUI
 * tests only use item and fluid keys.
 */
public final class GuiTestKeys {
    public static final Registry<AEKeyType> REGISTRY = create();

    private GuiTestKeys() {}

    private static Registry<AEKeyType> create() {
        var registry = new MappedRegistry<AEKeyType>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        return registry;
    }
}
