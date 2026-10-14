package cn.dancingsnow.neoecoae.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards server-side UI state against client-only class dependencies. */
class GuiModuleBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/cn/dancingsnow/neoecoae");

    @Test
    void controllerTransportAndStateDoNotLoadClientClasses() throws Exception {
        Pattern client = Pattern.compile(
            "(?:net\\.minecraft\\.client\\.|appeng\\.client\\.|org\\.lwjgl\\.|"
            + "cn\\.dancingsnow\\.neoecoae\\.client\\.)");
        for (String file : List.of("CraftingControllerSync.java", "CraftingControllerViewState.java",
                "CraftingControllerPanelState.java")) {
            Path path = ROOT.resolve("gui/crafting").resolve(file);
            assertFalse(client.matcher(Files.readString(path)).find(), path.toString());
        }
    }

    @Test
    void commonMenusDoNotLinkClientOnlyScreenImplementations() throws Exception {
        Pattern directReference = Pattern.compile(
            "(?m)^import (?:static )?cn\\.dancingsnow\\.neoecoae\\.client\\.");
        try (var files = Files.walk(ROOT.resolve("gui"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(directReference.matcher(Files.readString(file)).find(), file.toString());
            }
        }
    }
}
