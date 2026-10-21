package cn.dancingsnow.neoecoae.util;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the data-generation callbacks without loading optional mods or running runData. */
class ItemModelUtilTest {
    @Test
    void compatibilityCellsKeepHousingTierStatusAndAnimationInThatOrder() {
        for (var tier : List.of(
            List.of("16m", "l4"), List.of("64m", "l6"), List.of("256m", "l9"), List.of("4g", "l9"))) {
            assertEquals(List.of(
                "neoecoae:item/eco_cells/ae2omnicells/quantum_omni_cell_housing",
                "neoecoae:item/eco_cells/cell_light_" + tier.get(1),
                "neoecoae:item/eco_cells/cell_status_light",
                "neoecoae:item/eco_cells/ae2omnicells/quantum_omni_cell_layer"
            ), generatedTextures(provider -> ItemModelUtil.compatCellModel(
                "ae2omnicells", "quantum_omni_cell_housing", cn.dancingsnow.neoecoae.platform.JdkCompat.first(tier), "quantum_omni_cell_layer")
                .accept(context(), provider)));
        }
        assertEquals(List.of(
            "neoecoae:item/eco_cells/applied_create/andesite_cell_housing",
            "neoecoae:item/eco_cells/cell_light_l4",
            "neoecoae:item/eco_cells/cell_status_light"
        ), generatedTextures(provider -> ItemModelUtil.compatCellModel(
            "applied_create", "andesite_cell_housing", "16m").accept(context(), provider)));
        assertThrows(IllegalArgumentException.class,
            () -> ItemModelUtil.compatCellModel("megacells", "mega_item_cell_housing", "unknown"));
    }

    @Test
    void infiniteCellHasOnlyHousingAndStatusLight() {
        assertEquals(List.of(
            "neoecoae:item/eco_cells/infinite_resource_cell",
            "neoecoae:item/eco_cells/cell_status_light"
        ), generatedTextures(provider -> ItemModelUtil.infiniteCellModel("infinite_resource_cell")
            .accept(context(), provider)));
    }

    @Test
    void housingUsesItsModDirectoryAndHasNoTierOrStatusLight() {
        assertEquals(List.of(
            "neoecoae:item/eco_cells/ae2omnicells/quantum_omni_cell_housing",
            "neoecoae:item/eco_cells/ae2omnicells/quantum_omni_cell_layer"
        ), generatedTextures(provider -> ItemModelUtil.compatHousingModel(
            "ae2omnicells", "quantum_omni_cell_housing", "quantum_omni_cell_layer")
            .accept(context(), provider)));
    }

    @Test
    void feCellsKeepTheirExistingImportedItemModelIds() {
        var provider = provider();
        var context = context();
        when(context.getName()).thenReturn("eco_fe_cell_16m");
        ItemModelUtil.cellModel("fe", "16m").accept(context, provider);
        verify(provider).withExistingParent("eco_fe_cell_16m",
            new ResourceLocation("neoecoae", "item/cell/eco_energy_cell_16m"));
    }

    @SuppressWarnings("unchecked")
    private static DataGenContext<Item, Item> context() {
        return mock(DataGenContext.class);
    }

    private static RegistrateItemModelProvider provider() {
        var provider = mock(RegistrateItemModelProvider.class);
        when(provider.modLoc(anyString())).thenAnswer(call ->
            new ResourceLocation("neoecoae", call.getArgument(0)));
        return provider;
    }

    private static List<String> generatedTextures(java.util.function.Consumer<RegistrateItemModelProvider> generate) {
        var provider = provider();
        var textures = new AtomicReference<List<String>>();
        when(provider.generated(any(), any(ResourceLocation[].class))).thenAnswer(call -> {
            ResourceLocation[] layers = (ResourceLocation[]) call.getRawArguments()[1];
            textures.set(java.util.Arrays.stream(layers).map(ResourceLocation::toString).toList());
            return null;
        });
        generate.accept(provider);
        assertNotNull(textures.get(), "The callback must generate a model");
        return textures.get();
    }
}
