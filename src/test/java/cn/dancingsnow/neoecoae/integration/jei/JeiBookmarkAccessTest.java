package cn.dancingsnow.neoecoae.integration.jei;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiBookmarkAccessTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingJeiNeverLoadsJeiClassesEvenWithEmi(boolean emiInstalled) throws Exception {
        var modList = mock(ModList.class);
        when(modList.isLoaded("jei")).thenReturn(false);
        when(modList.isLoaded("emi")).thenReturn(emiInstalled);
        var forbiddenLoads = new ArrayList<String>();
        String entryPoint = "cn.dancingsnow.neoecoae.integration.jei.JeiBookmarkAccess";
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("mezz.jei.")
                        || name.equals(entryPoint + "Impl")
                        || name.equals("cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin")) {
                    forbiddenLoads.add(name);
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(entryPoint)) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        assertNotNull(input);
                        byte[] bytes = input.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(result);
                return result;
            }
        };
        try (var mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            Class<?> access = loader.loadClass(entryPoint);
            assertEquals(false, access.getMethod("isAvailable").invoke(null));
            assertEquals(List.of(), access.getMethod("itemBookmarks").invoke(null));
            access.getMethod("addMissingToBookmarks", List.class).invoke(null, List.of());
            assertTrue(forbiddenLoads.isEmpty(), () -> "Loaded optional JEI classes: " + forbiddenLoads);
        }
    }
}
