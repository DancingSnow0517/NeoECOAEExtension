package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.integration.jei.categories.CoolingCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.IntegrationWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.multiblock.MultiBlockInfoCategory;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiRecipeRegistrationTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void missingWorldRejectsRegistrationBeforePublishingRecipes() {
        var minecraft = mock(Minecraft.class);
        var registration = mock(IRecipeRegistration.class);
        try (var client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            var failure = assertThrows(NullPointerException.class,
                () -> new NeoECOAEJeiPlugin().registerRecipes(registration));
            assertTrue(failure.getMessage().contains("active client level"));
            verifyNoInteractions(registration);
        }
    }

    @Test
    void eachRegistrationUsesOneWorldAndTheNextCallbackUsesTheNewWorld() {
        var minecraft = mock(Minecraft.class);
        var first = mock(ClientLevel.class);
        var second = mock(ClientLevel.class);
        var firstRecipes = mock(RecipeManager.class);
        var secondRecipes = mock(RecipeManager.class);
        when(first.getRecipeManager()).thenReturn(firstRecipes);
        when(second.getRecipeManager()).thenReturn(secondRecipes);
        when(minecraft.getConnection()).thenThrow(new AssertionError("Do not reread the nullable global connection"));
        var registration = mock(IRecipeRegistration.class);
        try (var client = mockStatic(Minecraft.class);
                var cooling = mockStatic(CoolingCategory.class);
                var integrated = mockStatic(IntegrationWorkingStationCategory.class);
                var multiblock = mockStatic(MultiBlockInfoCategory.class);
                var large = mockStatic(LargeWorkstationRecipes.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            large.when(() -> LargeWorkstationRecipes.getAll(first)).thenReturn(List.of());
            large.when(() -> LargeWorkstationRecipes.getAll(second)).thenReturn(List.of());
            cooling.when(() -> CoolingCategory.registerRecipes(eq(registration), any(RecipeManager.class)))
                .thenAnswer(call -> { minecraft.level = null; return null; });
            var plugin = new NeoECOAEJeiPlugin();
            minecraft.level = first;
            plugin.registerRecipes(registration);
            minecraft.level = second;
            plugin.registerRecipes(registration);

            integrated.verify(() -> IntegrationWorkingStationCategory.registerRecipes(registration, firstRecipes));
            integrated.verify(() -> IntegrationWorkingStationCategory.registerRecipes(registration, secondRecipes));
            large.verify(() -> LargeWorkstationRecipes.getAll(first));
            large.verify(() -> LargeWorkstationRecipes.getAll(second));
            verify(registration, times(2)).addRecipes(NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE, List.of());
        }
    }
}
