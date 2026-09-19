package cn.dancingsnow.neoecoae.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Enforces source-module boundaries without introducing another Gradle source set. */
class CraftingModuleBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/cn/dancingsnow/neoecoae/crafting");

    @Test
    void amountsAndFormattersCompileWithoutTheModClasspath(@TempDir Path output) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on the project JDK");
        List<String> arguments = new ArrayList<>(
                List.of("--release", "17", "-proc:none", "-classpath", output.toString(), "-d", output.toString()));
        for (String directory : List.of("amount", "display/format")) {
            try (var files = Files.walk(ROOT.resolve(directory))) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(path -> arguments.add(path.toString()));
            }
        }
        assertEquals(
                0,
                compiler.run(null, null, null, arguments.toArray(String[]::new)),
                "Arithmetic and formatting must not acquire AE2, Minecraft or host dependencies");
    }

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
        var forbidden = Pattern.compile("(net\\.minecraft\\.client\\.|appeng\\.api\\.client\\.|org\\.lwjgl\\.|"
                + "cn\\.dancingsnow\\.neoecoae\\.crafting\\.graph\\.client\\.)");
        try (var files = Files.list(ROOT.resolve("graph"))) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
            }
        }
    }

    private static void assertNoReferences(Path directory, Pattern forbidden) throws Exception {
        try (var files = Files.walk(directory)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
            }
        }
    }
}
