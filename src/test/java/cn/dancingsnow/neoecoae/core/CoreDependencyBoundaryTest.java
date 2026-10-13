package cn.dancingsnow.neoecoae.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps the version-independent core free of game, loader and mod-internal dependencies. */
class CoreDependencyBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/cn/dancingsnow/neoecoae/core");
    private static final List<String> ALLOWED_IMPORT_PREFIXES = List.of(
        "java.", "it.unimi.dsi.fastutil.", "org.jetbrains.annotations.", "cn.dancingsnow.neoecoae.core.");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;");
    private static final Pattern FORBIDDEN_REFERENCE = Pattern.compile(
        "(?<![\\w.])(?:net\\.minecraft\\.|net\\.neoforged\\.|appeng\\.|com\\.lowdragmc\\.|com\\.mojang\\.|"
        + "com\\.tterrag\\.|cn\\.dancingsnow\\.neoecoae\\.(?!core(?!\\w)))");

    @Test
    void importsStayWithinAllowedPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : coreSources()) {
            List<String> lines = codeLines(Files.readAllLines(file));
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = IMPORT.matcher(lines.get(i));
                if (matcher.find() && !isAllowedImport(matcher.group(1))) {
                    violations.add(describe(file, i, lines.get(i)));
                }
            }
        }
        assertNoViolations(violations);
    }

    @Test
    void noFullyQualifiedReferencesToForbiddenPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : coreSources()) {
            List<String> lines = codeLines(Files.readAllLines(file));
            for (int i = 0; i < lines.size(); i++) {
                if (FORBIDDEN_REFERENCE.matcher(lines.get(i)).find()) {
                    violations.add(describe(file, i, lines.get(i)));
                }
            }
        }
        assertNoViolations(violations);
    }

    private static List<Path> coreSources() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            List<Path> sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            assertFalse(sources.isEmpty(), "no core sources found under " + ROOT.toAbsolutePath());
            return sources;
        }
    }

    private static boolean isAllowedImport(String target) {
        return ALLOWED_IMPORT_PREFIXES.stream().anyMatch(target::startsWith);
    }

    /** Returns the source lines with comments blanked out, keeping line numbers aligned with the file. */
    private static List<String> codeLines(List<String> lines) {
        List<String> code = new ArrayList<>(lines.size());
        boolean inBlockComment = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (inBlockComment) {
                inBlockComment = !trimmed.contains("*/");
                code.add("");
            } else if (trimmed.startsWith("/*")) {
                inBlockComment = !trimmed.contains("*/");
                code.add("");
            } else if (trimmed.startsWith("//")) {
                code.add("");
            } else {
                int lineComment = line.indexOf("//");
                code.add(lineComment < 0 ? line : line.substring(0, lineComment));
            }
        }
        return code;
    }

    private static String describe(Path file, int index, String line) {
        return file.toString().replace('\\', '/') + ":" + (index + 1) + ": " + line.trim();
    }

    private static void assertNoViolations(List<String> violations) {
        assertEquals(List.of(), violations,
            () -> "Core boundary violations:\n" + String.join("\n", violations));
    }
}
