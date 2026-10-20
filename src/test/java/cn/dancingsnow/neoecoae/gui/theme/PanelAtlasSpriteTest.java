package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PanelAtlasSpriteTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void allSixAtlasesAreRegisteredAndCachedSpritesObserveSubsequentProviderReplacements() throws Exception {
        Map<Field, Object> saved = new LinkedHashMap<>();
        for (Class<?> type : List.of(NETextures.class, NETextures.Crafting.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.setAccessible(true);
                    saved.put(field, field.get(null));
                }
            }
        }
        try {
            ResourceInstance<IGuiTexture> resources = mock(ResourceInstance.class);
            var provider = new AtomicReference<BuiltinResourceProvider<IGuiTexture>>();
            doAnswer(call -> { provider.set(call.getArgument(0)); return null; })
                .when(resources).addBuiltinProvider(any());
            when(resources.getResource(any())).thenAnswer(call -> provider.get().getResource(call.getArgument(0)));
            NETextures.init(resources);
            Map<String, String> atlases = Map.of(
                "Computation-BACKGROUND_ATLAS", "eco_cpu_controller.png",
                "Computation-WIDGETS_ATLAS", "cpu_widget.png",
                "Storage-CONTROLLER_ATLAS", "storage/storage_controller.png",
                "Storage-ATLAS", "storage/storage_widget.png",
                "Crafting-CONTROLLER_ATLAS", "crafting_controller.png",
                "Crafting-WIDGETS_ATLAS", "crafting_widgets.png");
            for (var atlas : atlases.entrySet()) {
                var original = NeoECOAE.id("textures/guis/" + atlas.getValue());
                var path = new BuiltinPath("ui-eco:" + atlas.getKey());
                assertNotNull(provider.get().getResource(path), atlas.getKey());
                var cached = assertInstanceOf(PanelAtlasSprite.class, NETextures.panelSprite(original)
                    .setSprite(7, 11, 29, 4).setBorder(1, 0, 1, 0).setColor(0xFFAABBCC).copy());
                assertEquals(original, cached.resolve().getImageLocation());
                var replacement = ResourceLocation.fromNamespaceAndPath("addon", "textures/gui/" + atlas.getValue());
                assertTrue(provider.get().addResource(atlas.getKey(), SpriteTexture.of(replacement)));
                SpriteTexture resolved = cached.resolve();
                assertEquals(replacement, resolved.getImageLocation());
                assertEquals(cached.spritePosition, resolved.spritePosition);
                assertEquals(cached.spriteSize, resolved.spriteSize);
                assertEquals(cached.borderLT, resolved.borderLT);
                assertEquals(cached.borderRB, resolved.borderRB);
                assertEquals(cached.color, resolved.color);

                IECOTier addon = mock(IECOTier.class);
                var custom = ResourceLocation.fromNamespaceAndPath("addon", "textures/tier/" + atlas.getValue());
                when(addon.getPanelAtlas(atlas.getKey())).thenReturn(custom);
                var activeTier = new AtomicReference<IECOTier>(addon);
                var scoped = assertInstanceOf(PanelAtlasSprite.class, NETextures.panelSprite(original, activeTier::get)
                    .setSprite(7, 11, 29, 4).setBorder(1, 0, 1, 0).setColor(0xFFAABBCC)
                    .setWrapMode(SpriteTexture.WrapMode.REPEAT).copy());
                SpriteTexture own = scoped.resolve();
                assertEquals(custom, own.getImageLocation());
                assertEquals(scoped.spritePosition, own.spritePosition);
                assertEquals(scoped.spriteSize, own.spriteSize);
                assertEquals(scoped.borderLT, own.borderLT);
                assertEquals(scoped.borderRB, own.borderRB);
                assertEquals(scoped.color, own.color);
                assertEquals(scoped.wrapMode, own.wrapMode);
                assertEquals(replacement, cached.resolve().getImageLocation(), "Addon atlas must not recolor other hosts");
                for (var builtin : ECOTier.values()) {
                    assertNull(builtin.getPanelAtlas(atlas.getKey()));
                    activeTier.set(builtin);
                    assertEquals(replacement, scoped.resolve().getImageLocation());
                }
                activeTier.set(addon);
                when(addon.getPanelAtlas(atlas.getKey())).thenReturn(null);
                assertEquals(replacement, scoped.resolve().getImageLocation());
            }
        } finally {
            for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }
}
