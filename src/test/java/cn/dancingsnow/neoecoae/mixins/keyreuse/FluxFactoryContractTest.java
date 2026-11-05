package cn.dancingsnow.neoecoae.mixins.keyreuse;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import static org.junit.jupiter.api.Assertions.*;

class FluxFactoryContractTest {
    @Test void installedFactoryMatchesButMutableOrChangedFactoriesAreRejected() throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "com/glodblock/github/appflux/common/me/key/FluxKey.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        assertTrue(KeyReuseMixinPlugin.supportsFluxFactory(node));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "mutable", "I", null, null));
        assertFalse(KeyReuseMixinPlugin.supportsFluxFactory(node));
        node.fields.removeLast();
        node.methods.removeIf(method -> method.name.equals("of"));
        assertFalse(KeyReuseMixinPlugin.supportsFluxFactory(node));
    }
}
