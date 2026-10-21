package cn.dancingsnow.neoecoae.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import static org.mockito.Mockito.*;

public final class RecipeTestSupport {
    private RecipeTestSupport() {}
    /** Forge stores ids on recipes rather than in a separate holder. Keep the supplied implementation. */
    public static <T extends Recipe<?>> T withId(ResourceLocation id,T recipe) {
        T identified=mockingDetails(recipe).isMock()?recipe:spy(recipe);
        doReturn(id).when(identified).getId();
        return identified;
    }
}
