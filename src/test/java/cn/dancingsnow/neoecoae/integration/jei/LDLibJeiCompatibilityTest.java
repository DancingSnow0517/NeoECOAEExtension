package cn.dancingsnow.neoecoae.integration.jei;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual dependency bytecode at the call site that crashed with JEI 19.52. */
class LDLibJeiCompatibilityTest {
    @Test
    void itemLookupCreatesTypedIngredientsWithoutLinkingJeiInternals() throws IOException {
        String path = "/com/lowdragmc/lowdraglib2/gui/ui/elements/ItemSlot$JEISupport.class";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            ClassNode type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            boolean publicFactory = false;
            for (var method : type.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        assertFalse(call.owner.startsWith("mezz/jei/library/"), call.owner);
                        publicFactory |= call.name.equals("createTypedIngredient");
                    }
                }
            }
            assertTrue(publicFactory, "Item lookup must create ingredients through the supported JEI API");
        }
    }
}
