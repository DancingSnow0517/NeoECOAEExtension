package cn.dancingsnow.neoecoae.compat.ae2;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECORemoteMachineUISideTest {
    private static final String UI_CLASS = "cn.dancingsnow.neoecoae.compat.ae2.ECORemoteMachineUI";

    @Test
    void commonFactoryRegistersWithoutLoadingMinecraftClientClasses() throws Exception {
        var id = new ResourceLocation("neoecoae", "remote_pattern_machine");
        var previous = UIFactory.FACTORIES.get(id);
        try {
            // A normal test JVM contains client classes. Reject them explicitly when verifying
            // our factory, as Forge's dedicated-server classloader does during mod construction.
            var loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("net.minecraft.client.")) {
                        throw new ClassNotFoundException("Client class on dedicated server: " + name);
                    }
                    if (!name.equals(UI_CLASS) && !name.startsWith(UI_CLASS + "$")) {
                        return super.loadClass(name, resolve);
                    }
                    synchronized (getClassLoadingLock(name)) {
                        var type = findLoadedClass(name);
                        if (type == null) {
                            try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (input == null) throw new ClassNotFoundException(name);
                                byte[] bytes = input.readAllBytes();
                                type = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException failure) {
                                throw new ClassNotFoundException(name, failure);
                            }
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
            };
            var type = assertDoesNotThrow(() -> Class.forName(UI_CLASS, true, loader));
            var initialize = type.getDeclaredMethod("initialize");
            initialize.setAccessible(true);
            assertDoesNotThrow(() -> initialize.invoke(null));
            var factory = UIFactory.FACTORIES.get(id);
            assertNotNull(factory);
            assertSame(loader, factory.getClass().getClassLoader());
        } finally {
            if (previous == null) UIFactory.FACTORIES.remove(id);
            else UIFactory.FACTORIES.put(id, previous);
        }
    }
}
