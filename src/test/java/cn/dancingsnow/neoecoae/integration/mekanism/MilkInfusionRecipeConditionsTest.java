package cn.dancingsnow.neoecoae.integration.mekanism;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MilkInfusionRecipeConditionsTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void recipeLoadsOnlyWhenAllThreeModsArePresent(int loadedMask) throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/data/neoecoae/recipes/compat/useless_stretcher/infinite_sand_cell.json"))).getAsJsonObject();
        List<String> mods = List.of("neoecoae", "mekanism", "useless_stretcher");
        ModList installed = mock(ModList.class);
        for (int i = 0; i < mods.size(); i++) when(installed.isLoaded(mods.get(i))).thenReturn((loadedMask & (1 << i)) != 0);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(installed);
            boolean enabled = true;
            for (var element : json.getAsJsonArray("conditions")) {
                var condition = element.getAsJsonObject();
                assertEquals("forge:mod_loaded", condition.get("type").getAsString());
                enabled &= ModLoadedCondition.Serializer.INSTANCE.read(condition).test(ICondition.IContext.EMPTY);
            }
            assertEquals(loadedMask == 7, enabled);
        }
    }
}
