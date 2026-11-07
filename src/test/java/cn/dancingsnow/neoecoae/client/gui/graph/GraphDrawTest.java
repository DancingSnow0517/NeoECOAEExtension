package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayout;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.*;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

class GraphDrawTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws Exception {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(cn.dancingsnow.neoecoae.registration.NERegistrate.class)) {
            registrate.when(() -> cn.dancingsnow.neoecoae.registration.NERegistrate.create("neoecoae"))
                .thenReturn(mock(cn.dancingsnow.neoecoae.registration.NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
    }

    @Test
    void longDiagonalAndTriangleUseConstantGeometryWithoutPerPixelDrawsOrFlushes() {
        var graphics = mock(GuiGraphics.class);
        var source = mock(MultiBufferSource.BufferSource.class);
        var vertices = mock(VertexConsumer.class, RETURNS_SELF);
        when(graphics.pose()).thenReturn(new PoseStack());
        when(graphics.bufferSource()).thenReturn(source);
        when(source.getBuffer(any())).thenReturn(vertices);
        GraphDraw.line(graphics, -1_000_000, -1_000_000, 1_000_000, 1_000_000, 0xff123456, 0.5f);
        verify(vertices, times(4)).vertex(any(org.joml.Matrix4f.class), anyFloat(), anyFloat(), eq(0f));
        GraphDraw.triangle(graphics, 0, 0, 10, 20, 20, 0, 0xff654321);
        verify(vertices, times(8)).vertex(any(org.joml.Matrix4f.class), anyFloat(), anyFloat(), eq(0f));
        verify(graphics, never()).flush();
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void recursiveCanvasUsesCompactMaterialBoxesAndKeepsDetailsInTooltips() throws Exception {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var fuel = AEItemKey.of(Items.COAL);
        assertEquals("−12", RecursiveCycleRenderer.signedExact(seed,
            cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.of(-12)));
        var cycle = new CycleGroup(4, List.of(0), List.of(), "SOLVED", List.of(new KeyAmount(seed, 1)),
            List.of(new KeyAmount(fuel, 3)), List.of(new KeyAmount(seed, 1)), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), ExecutionCountKnowledge.EXACT,
            CycleSolveStatus.SUCCESS, new Recursion(List.of(new KeyAmount(seed, 1)),
                List.of(new RecursionLayer(List.of(new KeyAmount(fuel, 3)), List.of(new KeyAmount(seed, 2)),
                    List.of(), false)), true));
        var snapshot = new CraftingGraphSnapshot(0,
            List.of(new MaterialNode(0, seed, 99, 1, 98, 0, MaterialStatus.CYCLE),
                new MaterialNode(1, fuel, 999, 999, 0, 0, MaterialStatus.SATISFIED)),
            List.of(new PatternNode(-2, "AEProcessingPattern:minecraft:iron_ingot", List.of(new Relationship(1, 3)),
                List.of(new Relationship(0, 2)), 1, CandidateStatus.SELECTED, null, 4)),
            List.of(), List.of(cycle), List.of(), new Summary("SUCCESS", 2, 1, 0, 1, 0));
        var graph = ClientCraftingGraph.cycle(snapshot, 4, false);
        var layout = new GraphLayout().layout(graph, 1);
        var client = mock(Minecraft.class);
        var font = spy(new Font(ignored -> null, false));
        doAnswer(call -> ((String) call.getArgument(0)).length() * 6).when(font).width(anyString());
        doAnswer(call -> {
            String value = call.getArgument(0); int width = call.getArgument(1);
            return value.substring(0, Math.min(value.length(), Math.max(0, width / 6)));
        }).when(font).plainSubstrByWidth(anyString(), anyInt());
        var fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(client, font);
        var graphics = mock(GuiGraphics.class);
        var source = mock(MultiBufferSource.BufferSource.class);
        when(graphics.pose()).thenReturn(new PoseStack());
        when(graphics.bufferSource()).thenReturn(source);
        when(source.getBuffer(any())).thenReturn(mock(VertexConsumer.class, RETURNS_SELF));
        var labels = new ArrayList<String>();
        when(graphics.drawString(eq(font), anyString(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenAnswer(call -> { labels.add(call.getArgument(1)); return 0; });
        var language = mock(net.minecraft.locale.Language.class);
        var translations = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
            getClass().getResourceAsStream("/assets/neoecoae/lang/zh_cn.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        when(language.getOrDefault(anyString())).thenAnswer(call -> translations.has(call.getArgument(0))
            ? translations.get((String) call.getArgument(0)).getAsString() : call.getArgument(0));
        when(language.getOrDefault(anyString(), anyString())).thenAnswer(call -> translations.has(call.getArgument(0))
            ? translations.get((String) call.getArgument(0)).getAsString() : call.getArgument(1));
        try (var minecraft = mockStatic(Minecraft.class); var icons = mockStatic(AEKeyRendering.class);
                var languages = mockStatic(net.minecraft.locale.Language.class);
                var renderer = new GraphRenderer()) {
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            languages.when(net.minecraft.locale.Language::getInstance).thenReturn(language);
            renderer.render(graphics, graph, layout, 400, 500, 1, 1200, 1000, -1, -1, null, new GraphProfiler());
            assertTrue(labels.stream().anyMatch(label -> label.contains("种子")), labels.toString());
            assertTrue(labels.contains("外部输入"), labels.toString());
            assertFalse(labels.contains("耗尽"), "Material cards only show an icon and amount");
            assertTrue(labels.stream().anyMatch(label -> label.contains("第 1 层")), labels.toString());
            assertTrue(labels.containsAll(List.of("0", "1", "2", "3")), labels.toString());
            assertFalse(labels.contains("99") || labels.contains("999"), "Whole-order quantities must not replace checkpoint amounts");
            icons.verify(() -> AEKeyRendering.drawInGui(eq(client), eq(graphics), anyInt(), anyInt(), any()), times(6));
            labels.clear();
            for (var node : graph.nodes().values()) if (node.key() != null) {
                RecursiveCycleRenderer.drawNode(graphics, graph, node, layout.box(node.id()), false, false, 0, 0, 1);
            }
            assertEquals(List.of("0", "1", "1", "1", "2", "3"), labels.stream().sorted().toList(),
                "Compact cards must not draw a role header, item name or change line");
            var depleted = graph.nodes().values().stream().filter(node -> node.key() != null
                && node.recursion().role() == cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role.RETAINED
                && node.recursion().amount().isZero()).findFirst().orElseThrow();
            icons.when(() -> AEKeyRendering.getTooltip(any())).thenReturn(List.of());
            assertTrue(RecursiveCycleRenderer.tooltip(graph, depleted).stream()
                .anyMatch(line -> line.getString().contains("耗尽")));
            var step = graph.nodes().values().stream().filter(node -> node.recursion().role() == Role.STEP).findFirst().orElseThrow();
            clearInvocations(graphics, source);
            labels.clear();
            RecursiveCycleRenderer.drawNode(graphics, graph, step, layout.box(step.id()), true, true, 0, 0, 1);
            assertTrue(labels.isEmpty(), "Execution icons keep all labels in their tooltip");
            verify(graphics).blit(eq(cn.dancingsnow.neoecoae.gui.theme.ECOIcon.ATLAS), eq(2), eq(2), eq(0),
                eq(48f), eq(144f), eq(16), eq(16), eq(256), eq(256));
            verify(source, never()).getBuffer(any());
            var water = spy(AEFluidKey.of(Fluids.WATER));
            doReturn(net.minecraft.network.chat.Component.literal("水")).when(water).getDisplayName();
            var folded = new RecursionLayer(List.of(new KeyAmount(fuel, 21_851_672),
                new KeyAmount(water, 201_385_009_152L)), List.of(),
                List.of(new PatternAmount(-2, 2_731_459)), true);
            var tooltip = RecursiveCycleRenderer.layerTooltip(graph, folded, 13);
            var tooltipText = tooltip.stream().map(net.minecraft.network.chat.Component::getString).toList();
            assertEquals("第 13 层 │ 合并节点", tooltipText.get(0));
            assertTrue(tooltipText.contains("◆ 加工配方 × 2,731,459"), tooltipText.toString());
            assertTrue(tooltipText.contains("  " + seed.getDisplayName().getString()), "Show the recipe output name on its own line");
            assertTrue(tooltipText.contains("  AEProcessingPattern:minecraft:iron_ingot"));
            assertTrue(tooltipText.stream().anyMatch(line -> line.endsWith(" × 21,851,672")));
            assertTrue(tooltipText.stream().anyMatch(line -> line.endsWith(" × 201,385,009.152 B")));
            assertTrue(tooltipText.contains("✔ 重复过程已合并 · 需求统计已汇总"));
            var styles = new java.util.HashMap<String, net.minecraft.network.chat.Style>();
            tooltip.forEach(line -> line.visit((style, text) -> {
                styles.put(text, style);
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY));
            assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(), styles.get("合并节点").getColor().getValue());
            assertFalse(styles.get("合并节点").isBold(), "Only the layer number is bold");
            assertEquals(ChatFormatting.DARK_GRAY.getColor(), styles.get("  AEProcessingPattern:minecraft:iron_ingot").getColor().getValue());
            assertEquals(ChatFormatting.WHITE.getColor(), styles.get("21,851,672").getColor().getValue());
            assertEquals(ChatFormatting.AQUA.getColor(), styles.get("201,385,009.152 B").getColor().getValue());
            assertFalse(RecursiveCycleRenderer.layerTooltip(graph,
                new RecursionLayer(folded.addedInputs(), folded.inventory(), folded.patternTimes(), false), 12).stream()
                .anyMatch(line -> line.getString().contains("合并")), "An ordinary layer must not claim to be merged");
        }
    }
}
