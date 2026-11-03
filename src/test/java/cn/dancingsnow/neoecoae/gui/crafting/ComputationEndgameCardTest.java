package cn.dancingsnow.neoecoae.gui.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationEndgameCardTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void endgameStatusBecomesAVanillaFramedChecklistCard() throws ReflectiveOperationException {
        try (var minecraft = mockStatic(Minecraft.class); var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            LayoutProperties.init();
            var client = mock(Minecraft.class);
            Font font = mock(Font.class);
            when(font.width(org.mockito.ArgumentMatchers.any(FormattedText.class)))
                .thenAnswer(call -> ((FormattedText) call.getArgument(0)).getString().length() * 6);
            var field = Minecraft.class.getDeclaredField("font");
            field.setAccessible(true);
            field.set(client, font);
            minecraft.when(Minecraft::getInstance).thenReturn(client);

            // Same shape as ECOComputationSystemBlockEntity#getEndgameStatusTooltip.
            MutableComponent status = Component.translatable("gui.neoecoae.computation.endgame.title");
            status.append("\n").append(Component.translatable("gui.neoecoae.computation.endgame.reason.host_count", 1, 8));
            status.append("\n").append(Component.translatable("gui.neoecoae.computation.endgame.reason.drives"));
            assertTrue(ComputationEndgameCard.applies(status));
            var card = ComputationEndgameCard.create(status);
            assertEquals(2, card.size());
            var title = (Component) card.get(0);
            assertTrue(title.getSiblings().stream().anyMatch(part -> part.getContents() instanceof TranslatableContents text
                && "gui.neoecoae.crafting.ui.virtual_card.unmet".equals(text.getKey()) && Integer.valueOf(2).equals(text.getArgs()[0])),
                title::toString);
            assertInstanceOf(ModularUITooltipComponent.class, card.get(1));

            assertTrue(ComputationEndgameCard.applies(Component.translatable("gui.neoecoae.computation.endgame.active")));
            assertFalse(ComputationEndgameCard.applies(Component.literal("other")));
        }
    }
}
