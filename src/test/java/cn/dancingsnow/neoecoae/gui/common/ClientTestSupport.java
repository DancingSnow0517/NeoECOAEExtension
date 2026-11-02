package cn.dancingsnow.neoecoae.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 26.1 port: LDLib2 26.1 lays text out eagerly ({@code TextElement#setText} → {@code LDFontManager}), and its
 * font manager reads {@code Minecraft.getInstance().options} (unicode / Japanese glyph options). The isolated
 * tests have no client, so they supply a mocked {@link Minecraft} carrying default options.
 */
public final class ClientTestSupport {
    private ClientTestSupport() {}

    /** Options with the two font flags LDLib2 reads, both off (vanilla defaults). */
    public static Options options() {
        // Build the values before stubbing: creating them may touch other (static) mocks.
        OptionInstance<Boolean> unicode = OptionInstance.createBoolean("options.forceUnicodeFont", false);
        OptionInstance<Boolean> japanese = OptionInstance.createBoolean("options.japaneseGlyphVariants", false);
        Options options = mock(Options.class);
        when(options.forceUnicodeFont()).thenReturn(unicode);
        when(options.japaneseGlyphVariants()).thenReturn(japanese);
        return options;
    }

    /** Writes default {@link #options()} into a (mocked) client's final {@code options} field. */
    public static Minecraft withOptions(Minecraft client) {
        setField(client, "options", options());
        return client;
    }

    /** {@code Minecraft.getInstance()} returns a mocked client with default options; close it after the test. */
    public static MockedStatic<Minecraft> minecraft() {
        Minecraft client = withOptions(mock(Minecraft.class));
        MockedStatic<Minecraft> minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        return minecraft;
    }

    public static void setField(Object target, String name, Object value) {
        try {
            Field field = Minecraft.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
