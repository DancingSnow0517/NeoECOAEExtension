package cn.dancingsnow.neoecoae.dev;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static net.minecraft.commands.Commands.literal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DevKeyBenchmarkCommandTest {
    @Test void defaultAndExplicitCommandsParseWithoutRunningGameCode() {
        var dispatcher = dispatcher();
        for (String command : new String[] {"neodev keybench", "neodev keybench item", "neodev keybench fluid 256",
                "neodev keybench both 8192 1200", "neodev keybench_status", "neodev keybench_stop", "neodev keybench_report"}) {
            var parsed = dispatcher.parse(command, mock(CommandSourceStack.class));
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
    }

    @Test void rejectsUnknownChannelOrExcessivePerTickWork() {
        var dispatcher = dispatcher();
        for (String command : new String[] {"neodev keybench unknown", "neodev keybench item 0",
                "neodev keybench item 8193", "neodev keybench both 1 0", "neodev keybench both 1 1201"}) {
            var parsed = dispatcher.parse(command, mock(CommandSourceStack.class));
            assertTrue(parsed.getReader().canRead(), command);
        }
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var root = literal("neodev");
        DevKeyBenchmark.commands(root);
        dispatcher.register(root);
        return dispatcher;
    }
}
