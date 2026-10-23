package cn.dancingsnow.neoecoae.gui.theme;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.BuiltinPath;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.BuiltinResourceProvider;
import cn.dancingsnow.neoecoae.platform.ui.runtime.editor.resource.ResourceInstance;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
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
            when(resources.getResource(any())).thenAnswer(call -> provider.get().getResource(call.<BuiltinPath>getArgument(0).path().substring("ui-eco:".length())));
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
                assertNotNull(provider.get().getResource(atlas.getKey()), atlas.getKey());
                var cached = assertInstanceOf(PanelAtlasSprite.class, NETextures.panelSprite(original)
                    .setSprite(7, 11, 29, 4).setBorder(1, 0, 1, 0).setColor(0xFFAABBCC).copy());
                assertEquals(original, cached.resolve().getImageLocation());
                var replacement = new ResourceLocation("addon", "textures/gui/" + atlas.getValue());
                provider.get().addResource(atlas.getKey(), SpriteTexture.of(replacement));
                assertNotNull(provider.get().getResource(atlas.getKey()));
                SpriteTexture resolved = cached.resolve();
                assertEquals(replacement, resolved.getImageLocation());
                assertEquals(cached.spritePosition.x, resolved.spritePosition.x);
                assertEquals(cached.spritePosition.y, resolved.spritePosition.y);
                assertEquals(cached.spriteSize.width, resolved.spriteSize.width);
                assertEquals(cached.spriteSize.height, resolved.spriteSize.height);
                assertEquals(cached.borderLT.x, resolved.borderLT.x);
                assertEquals(cached.borderLT.y, resolved.borderLT.y);
                assertEquals(cached.borderRB.x, resolved.borderRB.x);
                assertEquals(cached.borderRB.y, resolved.borderRB.y);
                assertEquals(cached.getColor(), resolved.getColor());

                IECOTier addon = mock(IECOTier.class);
                var custom = new ResourceLocation("addon", "textures/tier/" + atlas.getValue());
                when(addon.getPanelAtlas(atlas.getKey())).thenReturn(custom);
                var activeTier = new AtomicReference<IECOTier>(addon);
                var scoped = assertInstanceOf(PanelAtlasSprite.class, NETextures.panelSprite(original, activeTier::get)
                    .setSprite(7, 11, 29, 4).setBorder(1, 0, 1, 0).setColor(0xFFAABBCC)
                    .setWrapMode(SpriteTexture.WrapMode.REPEAT).copy());
                SpriteTexture own = scoped.resolve();
                assertEquals(custom, own.getImageLocation());
                assertEquals(scoped.spritePosition.x, own.spritePosition.x);
                assertEquals(scoped.spritePosition.y, own.spritePosition.y);
                assertEquals(scoped.spriteSize.width, own.spriteSize.width);
                assertEquals(scoped.spriteSize.height, own.spriteSize.height);
                assertEquals(scoped.borderLT.x, own.borderLT.x);
                assertEquals(scoped.borderLT.y, own.borderLT.y);
                assertEquals(scoped.borderRB.x, own.borderRB.x);
                assertEquals(scoped.borderRB.y, own.borderRB.y);
                assertEquals(scoped.getColor(), own.getColor());
                assertEquals(scoped.getWrapMode(), own.getWrapMode());
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
