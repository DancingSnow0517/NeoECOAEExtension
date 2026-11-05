package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Enumerates plain registry entries, never creative variants or component-bearing stacks. */
final class GeneratorPool {
    private static List<AEKey> cached;

    static void clear() { cached = null; }

    static List<AEKey> keys() {
        if (cached != null) return cached;
        var keys = new LinkedHashSet<AEKey>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR && !BuiltInRegistries.ITEM.getKey(item).toString().equals("neoecoae:dev_resource_generator")) {
                AEKey key = ECOKeyFactory.item(item);
                if (key != null && !key.hasComponents()) keys.add(key);
            }
        }
        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid != Fluids.EMPTY && (!(fluid instanceof FlowingFluid flowing) || flowing.getSource() == fluid)) {
                AEKey key = ECOKeyFactory.fluid(fluid);
                if (key != null) keys.add(key);
            }
        }
        // Optional AE channels (including Mekanism chemicals) expose registry-entry factories.
        // Inspect only already registered key classes, so absent mods are never linked.
        for (AEKeyType type : AEKeyTypes.getAll()) {
            if (type == AEKeyType.items() || type == AEKeyType.fluids()) continue;
            for (var factory : type.getKeyClass().getMethods()) {
                if (!Modifier.isStatic(factory.getModifiers()) || !factory.getName().equals("of")
                        || factory.getParameterCount() != 1 || !AEKey.class.isAssignableFrom(factory.getReturnType())) continue;
                Class<?> parameter = factory.getParameterTypes()[0];
                var constructors = stackConstructors(parameter);
                for (var registry : BuiltInRegistries.REGISTRY) {
                    for (Object entry : registry) {
                        try {
                            Object argument = parameter.isInstance(entry) ? entry : null;
                            for (var constructor : constructors) {
                                if (constructor.getParameterTypes()[0].isInstance(entry)) {
                                    argument = constructor.newInstance(entry, 1L);
                                    break;
                                }
                            }
                            if (argument == null) continue;
                            AEKey key = (AEKey) factory.invoke(null, argument);
                            if (key != null && !key.hasComponents()) keys.add(key);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            LoggerFactory.getLogger(GeneratorPool.class).warn(
                                "Skipping dev generator factory {} for {}", factory, entry, error);
                        }
                    }
                }
            }
        }
        cached = List.copyOf(keys);
        return cached;
    }

    private static List<Constructor<?>> stackConstructors(Class<?> parameter) {
        var classes = new ArrayList<Class<?>>();
        classes.add(parameter);
        // Mekanism 10.3 uses an abstract ChemicalStack and four concrete registry types.
        if (parameter.getName().equals("mekanism.api.chemical.ChemicalStack") && Modifier.isAbstract(parameter.getModifiers())) {
            for (String name : List.of("gas.GasStack", "infuse.InfusionStack", "pigment.PigmentStack", "slurry.SlurryStack")) {
                try {
                    classes.add(Class.forName("mekanism.api.chemical." + name, false, parameter.getClassLoader()));
                } catch (ClassNotFoundException error) {
                    throw new IllegalStateException("Registered chemical channel is missing its stack API", error);
                }
            }
        }
        var result = new ArrayList<Constructor<?>>();
        for (Class<?> type : classes) {
            if (Modifier.isAbstract(type.getModifiers())) continue;
            for (var constructor : type.getConstructors()) {
                if (constructor.getParameterCount() == 2 && constructor.getParameterTypes()[1] == long.class) result.add(constructor);
            }
        }
        return result;
    }
}
