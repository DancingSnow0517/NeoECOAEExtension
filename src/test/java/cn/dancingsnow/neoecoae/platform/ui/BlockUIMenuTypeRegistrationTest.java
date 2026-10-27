package cn.dancingsnow.neoecoae.platform.ui;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BlockUIMenuTypeRegistrationTest {
    private static final String UI_CLASS =
        "cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void freshFactoryRegistersWithoutOpeningAUi(boolean clientClassesAvailable) throws Exception {
        var id = ResourceLocation.fromNamespaceAndPath("neoecoae", "block_ui");
        var previous = UIFactory.FACTORIES.remove(id);
        try {
            // Reload the factory so a previous singleplayer session cannot hide missing registration.
            var loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!clientClassesAvailable && name.startsWith("net.minecraft.client.")) {
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
            var type = Class.forName(UI_CLASS, true, loader);
            var register = type.getMethod("register");
            assertDoesNotThrow(() -> register.invoke(null));
            var factory = UIFactory.FACTORIES.get(id);
            assertNotNull(factory, "The UI-open packet must find the factory before any player opens a UI");
            assertSame(loader, factory.getClass().getClassLoader());
            assertDoesNotThrow(() -> register.invoke(null));
            assertSame(factory, UIFactory.FACTORIES.get(id));
        } finally {
            if (previous == null) UIFactory.FACTORIES.remove(id);
            else UIFactory.FACTORIES.put(id, previous);
        }
    }
}
