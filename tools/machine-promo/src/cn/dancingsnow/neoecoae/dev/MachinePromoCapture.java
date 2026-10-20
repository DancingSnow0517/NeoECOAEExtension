package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.client.rendering.FixedBlockEntityRenderers;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Captures actual geometry and the normal synchronized controller menus, in en_us. */
@EventBusSubscriber(modid = "neoecoae", value = Dist.CLIENT)
public final class MachinePromoCapture {
    public static final int BACKGROUND = 0xFF10141C;
    private static int index = Integer.getInteger("neoecoae.promo.start", 0);
    private static int phase;
    private static int frames;
    private static int age;
    private static int totalTicks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !MachinePromoScene.ready) return;
        if (++totalTicks > 2400) throw new IllegalStateException("Machine marketing capture timed out");
        mc.options.hideGui = true;
        mc.options.pauseOnLostFocus = false;
        if (index >= 9) {
            if (++age > 30) mc.stop();
            return;
        }
        if (phase == 0) {
            // Load each real machine into the client before rendering its block entities.
            MachinePromoScene.requestedUi = index;
            phase = 1;
            age = 0;
        } else if (phase == 1 && MachinePromoScene.openedUi == index
            && mc.screen != null && !(mc.screen instanceof HeroScreen)) {
            if (++age > 50) {
                frames = 0;
                phase = 2;
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
            }
        } else if (phase == 3) {
            MachinePromoScene.closeUi = true;
            mc.setScreen(new HeroScreen(index));
            frames = 0;
            phase = 4;
        } else if (phase == 5) {
            mc.setScreen(null);
            index++;
            phase = 0;
            age = 0;
        }
    }

    @SubscribeEvent
    public static void background(ScreenEvent.BackgroundRendered event) {
        if (MachinePromoScene.ready && index < 9 && phase >= 1 && phase <= 2) {
            var mc = Minecraft.getInstance();
            event.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), BACKGROUND);
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !MachinePromoScene.ready || index >= 9) return;
        if (phase == 2 && ++frames >= 3 && mc.screen != null) {
            capture("promo-" + MachinePromoScene.key(index) + "-ui.png");
            phase = 3;
        } else if (phase == 4 && ++frames >= 10 && mc.screen instanceof HeroScreen) {
            capture("promo-" + MachinePromoScene.key(index) + "-model.png");
            phase = 5;
        }
    }

    private static void capture(String filename) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(),
            message -> System.out.println("MACHINE_PROMO_CAPTURE " + filename + " " + message.getString()));
    }

    private static final class HeroScreen extends Screen {
        private final int machine;
        private HeroScreen(int machine) {
            super(Component.literal("NeoECOAE machine marketing renderer"));
            this.machine = machine;
        }
        @Override public boolean isPauseScreen() { return false; }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, BACKGROUND);
            var pose = graphics.pose();
            var mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            BlockPos origin = MachinePromoScene.ORIGINS[machine];
            pose.pushPose();
            pose.translate(width / 2f, height / 2f + 10, 550);
            pose.scale(40, -40, 40);
            pose.mulPose(Axis.XP.rotationDegrees(23));
            pose.mulPose(Axis.YP.rotationDegrees(145));
            pose.translate(1, -1.5, -1);
            Lighting.setupFor3DItems();
            for (BlockPos pos : MachinePromoScene.BLOCKS.get(machine)) {
                var state = mc.level.getBlockState(pos);
                if (state.getRenderShape() == RenderShape.MODEL) {
                    pose.pushPose();
                    pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
                    mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
                var be = mc.level.getBlockEntity(pos);
                if (be != null) {
                    FixedBlockEntityRenderers.render(mc.level, be, 0, pose, buffers,
                        0xF000F0, OverlayTexture.NO_OVERLAY, origin);
                }
            }
            graphics.flush();
            pose.popPose();
        }
    }
}
