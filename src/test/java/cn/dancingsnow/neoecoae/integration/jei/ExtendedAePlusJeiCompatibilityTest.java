package cn.dancingsnow.neoecoae.integration.jei;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.*;

/** Checks dependency classes loaded during JEI startup and bookmark overlay clicks. */
class ExtendedAePlusJeiCompatibilityTest {
    @Test
    void bookmarkOverlayDoesNotLinkMissingJeiInputClasses() throws IOException {
        for (String name : Set.of(
                "com/extendedae_plus/mixin/jei/BookmarkOverlayMixin",
                "com/extendedae_plus/client/jei/JeiNetworkOverlayButton")) {
            assertReferencedClassesAvailable(name, "mezz/jei/");
        }
    }

    @Test
    void dataEnergisticsEncodingHooksDoNotLinkRemovedEaepUploadClasses() throws IOException {
        for (String name : Set.of(
                "com/fish_dan_/data_energistics/mixin/ae/extendedaeplus/EaepPatternEncodingTermMenuMixin",
                "com/fish_dan_/data_energistics/mixin/ae/extendedaeplus/EaepPatternUploadRecordMixin")) {
            assertReferencedClassesAvailable(name, "com/extendedae_plus/");
        }
    }

    private void assertReferencedClassesAvailable(String name, String prefix) throws IOException {
        ClassNode type = read(name);
        for (var method : type.methods) {
            for (var instruction : method.instructions) {
                String owner = switch (instruction) {
                    case MethodInsnNode call -> call.owner;
                    case FieldInsnNode field -> field.owner;
                    case TypeInsnNode reference -> reference.desc;
                    default -> "";
                };
                if (owner.startsWith(prefix)) {
                    assertNotNull(getClass().getResource("/" + owner + ".class"),
                        () -> name + "." + method.name + " links missing dependency class " + owner);
                }
            }
        }
    }

    private static ClassNode read(String name) throws IOException {
        try (var input = ExtendedAePlusJeiCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
