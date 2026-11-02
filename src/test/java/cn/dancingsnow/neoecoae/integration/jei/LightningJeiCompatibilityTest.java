package cn.dancingsnow.neoecoae.integration.jei;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the installed Lightning mixin, rather than only ECO's own injection targets. */
class LightningJeiCompatibilityTest {
    @Test
    void transferHooksDoNotReadThisAfterJeiReusesItsLocalSlot() throws IOException {
        var lightning = read("com/moakiee/ae2lt/mixin/recipeviewer/jei/JeiRecipeTransferButtonControllerMixin");
        int hooks = 0;
        for (var method : lightning.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) {
                if (annotation.values == null) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!annotation.values.get(i).equals("method")) continue;
                    Object targets = annotation.values.get(i + 1);
                    List<?> names = targets instanceof List<?> list ? list : List.of(targets);
                    if (names.stream().noneMatch(name -> name.toString().startsWith("onPress"))) continue;
                    hooks++;
                    assertTrue((method.access & Opcodes.ACC_STATIC) != 0,
                        () -> method.name + " reads this from a local slot optimized JEI can reuse");
                }
            }
        }
        assertTrue(hooks > 0, "Verify the actual transfer hooks supplied by the development dependency");
    }

    private static ClassNode read(String name) throws IOException {
        try (var input = LightningJeiCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
