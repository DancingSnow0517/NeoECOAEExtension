package cn.dancingsnow.neoecoae.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Enforces source-module boundaries without introducing another Gradle source set. */
class CraftingModuleBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/cn/dancingsnow/neoecoae/crafting");

    @Test
    void planningAndExecutionDoNotLoadClientCode() throws Exception {
        var forbidden = Pattern.compile(
            "(net\\.minecraft\\.client\\.|appeng\\.client\\.|appeng\\.api\\.client\\.|org\\.lwjgl\\.|"
            + "cn\\.dancingsnow\\.neoecoae\\.(client\\.|crafting\\.graph\\.client\\.))");
        for (String directory : List.of("planner", "execution", "adapter", "amount", "display/format")) {
            assertNoReferences(ROOT.resolve(directory), forbidden);
        }
    }

    @Test
    void graphProjectionCannotCallItsRenderer() throws Exception {
        var forbidden = Pattern.compile(
            "(net\\.minecraft\\.client\\.|appeng\\.api\\.client\\.|org\\.lwjgl\\.|"
            + "cn\\.dancingsnow\\.neoecoae\\.crafting\\.graph\\.client\\.)");
        try (var files = Files.list(ROOT.resolve("graph"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
            }
        }
    }

    private static void assertNoReferences(Path directory, Pattern forbidden) throws Exception {
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
            }
        }
    }
}
