package cn.dancingsnow.neoecoae.platform.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.syncdata.rpc.RPCSender;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedBlockEntityFieldsTest {
    @Test
    void annotatedRpcMethodsAreAvailableOnTheRecommendedLdlib() throws Exception {
        verify(new PreviewHost());
    }

    @Test
    void annotatedRpcMethodsAreAvailableOnTheActualMonifactoryLdlib() throws Exception {
        String path = System.getenv("NEOECOAE_LDLIB_LEGACY_JAR");
        assumeTrue(path != null && !path.isBlank(), "Supply Monifactory's embedded LDLib JAR");
        var parent = getClass().getClassLoader();
        String fields = ManagedBlockEntityFields.class.getName();
        String fixtures = getClass().getName() + "$";
        try (var loader = new URLClassLoader(new URL[]{Path.of(path).toUri().toURL()}, parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!name.startsWith("com.lowdragmc.lowdraglib.") && !name.equals(fields)
                            && !name.startsWith(fields + "$") && !name.startsWith(fixtures))
                        return super.loadClass(name, resolve);
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        if (name.startsWith("com.lowdragmc.lowdraglib.")) type = findClass(name);
                        else try (var stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            assertNotNull(stream, name);
                            byte[] bytes = stream.readAllBytes();
                            type = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        }) {
            verify(loader.loadClass(PreviewHost.class.getName()).getConstructor().newInstance());
        }
    }

    private void verify(Object host) throws Exception {
        var holder = host.getClass().getMethod("getFieldHolder").invoke(host);
        @SuppressWarnings("unchecked")
        var methods = (Map<String, ?>) holder.getClass().getMethod("getRpcMethodMap").invoke(holder);
        assertTrue(methods.containsKey("openPatternPreview"), "The preview handshake must be registered");
        assertTrue(methods.containsKey("inheritedPreview"), "Inherited RPC methods must remain registered");
        assertFalse(methods.containsKey("ordinaryMethod"));
        assertFalse(methods.containsKey("staticMethod"));
        assertSame(holder, host.getClass().getMethod("getFieldHolder").invoke(host));
        var loader = host.getClass().getClassLoader();
        var senderClass = loader.loadClass(RPCSender.class.getName());
        Object sender = senderClass.getMethod("ofServer").invoke(null);
        for (String name : new String[]{"openPatternPreview", "inheritedPreview"}) {
            Object method = methods.get(name);
            Object payloads = method.getClass().getMethod("serializeArgs", Object[].class)
                    .invoke(method, new Object[]{new Object[0]});
            method.getClass().getMethod("invoke", Object.class, senderClass, payloads.getClass())
                    .invoke(method, host, sender, payloads);
        }
        assertEquals(11, host.getClass().getField("received").getInt(host));
    }

    public static class ParentHost implements IManaged {
        public int received;
        @Override public ManagedFieldHolder getFieldHolder() { return ManagedBlockEntityFields.get(getClass()); }
        @Override public IManagedStorage getSyncStorage() { return null; }
        @Override public void onChanged() {}
        @RPCMethod public void inheritedPreview(RPCSender sender) { received++; }
    }

    public static class PreviewHost extends ParentHost {
        @RPCMethod public void openPatternPreview(RPCSender sender) { received += 10; }
        public void ordinaryMethod() {}
        @RPCMethod public static void staticMethod() {}
    }
}
