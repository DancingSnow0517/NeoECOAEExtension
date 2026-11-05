package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class KeyReuseMixinScopeTest {
    @Test void configuredAdaptersOnlyProtectEcoOwnedInstanceViews() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (var resource = loader.getResourceAsStream("neoecoae.key_reuse.mixins.json")) {
            assertNotNull(resource);
            var config = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : config.getAsJsonArray("mixins")) {
                String name = entry.getAsString();
                if (name.equals("FluxKeyReuseMixin") || name.equals("FluxKeyConstructorInvoker")) continue;
                assertTrue(name.equals("MekanismKeyReuseMixin") || name.equals("MatterKeyReuseMixin"));
                String path = "cn/dancingsnow/neoecoae/mixins/keyreuse/" + name + ".class";
                try (var bytecode = loader.getResourceAsStream(path)) {
                    assertNotNull(bytecode);
                    ClassNode node = new ClassNode();
                    new ClassReader(bytecode).accept(node, 0);
                    for (var method : node.methods) {
                        if (method.visibleAnnotations == null) continue;
                        for (var annotation : method.visibleAnnotations) {
                            if (!annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")) continue;
                            assertEquals("Lorg/spongepowered/asm/mixin/injection/Inject;", annotation.desc);
                            assertEquals(0, method.access & Opcodes.ACC_STATIC, "no static factory or decoder interception");
                            int index = annotation.values.indexOf("method");
                            assertTrue(index >= 0);
                            for (Object target : (java.util.List<?>) annotation.values.get(index + 1)) {
                                assertTrue(target.equals("getStack") || target.equals("hashCode"), target.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}
