package cn.dancingsnow.neoecoae.integration.xei;

import net.minecraft.resources.ResourceLocation;

/** Viewer filtering never removes the actual server-side machine recipe. */
public final class HiddenEasterEggRecipes {
    public static final ResourceLocation INFINITE_SAND =
        new ResourceLocation("neoecoae", "compat/useless_stretcher/infinite_sand_cell");

    private HiddenEasterEggRecipes() {}

    public static boolean isHidden(ResourceLocation id) {
        // EMI's JEI bridge prefixes the original id, and includes recipes hidden by JEI.
        return INFINITE_SAND.equals(id) || id != null && id.getNamespace().equals("jei")
            && id.getPath().equals("/" + INFINITE_SAND.getNamespace() + "/" + INFINITE_SAND.getPath());
    }
}
