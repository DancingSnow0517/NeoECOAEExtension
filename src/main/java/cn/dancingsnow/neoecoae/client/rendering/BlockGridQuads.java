package cn.dancingsnow.neoecoae.client.rendering;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import java.util.ArrayList;
import java.util.List;

/** Subdivides axis-aligned model faces at block boundaries without changing their texture mapping. */
final class BlockGridQuads {
    private static final float EPSILON = 0.0001F;

    private BlockGridQuads() {}

    record Part(BakedQuad quad, BlockPos origin, float[] brightness) {}

    static List<Part> split(BakedQuad quad, float[] brightness) {
        int[] vertices = quad.getVertices();
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                float coordinate = coordinate(vertices, vertex, axis);
                min[axis] = Math.min(min[axis], coordinate);
                max[axis] = Math.max(max[axis], coordinate);
            }
        }
        int normalAxis = quad.getDirection().getAxis().ordinal();
        int firstAxis = (normalAxis + 1) % 3;
        int secondAxis = (normalAxis + 2) % 3;
        if (max[normalAxis] - min[normalAxis] > EPSILON
            || max[firstAxis] - min[firstAxis] < EPSILON || max[secondAxis] - min[secondAxis] < EPSILON) {
            return List.of(part(quad, brightness));
        }
        if (Mth.floor(min[firstAxis] + EPSILON) == Mth.floor(max[firstAxis] - EPSILON)
            && Mth.floor(min[secondAxis] + EPSILON) == Mth.floor(max[secondAxis] - EPSILON)) {
            return List.of(part(quad, brightness));
        }
        // Rotated rectangles and sloped faces are not a Cartesian product of two intervals.
        for (int vertex = 0; vertex < 4; vertex++) {
            if (!isEnd(coordinate(vertices, vertex, firstAxis), min[firstAxis], max[firstAxis])
                || !isEnd(coordinate(vertices, vertex, secondAxis), min[secondAxis], max[secondAxis])) {
                return List.of(part(quad, brightness));
            }
        }
        List<Part> parts = new ArrayList<>();
        for (float first = min[firstAxis]; first < max[firstAxis] - EPSILON;) {
            float firstEnd = Math.min(max[firstAxis], Mth.floor(first + EPSILON) + 1);
            for (float second = min[secondAxis]; second < max[secondAxis] - EPSILON;) {
                float secondEnd = Math.min(max[secondAxis], Mth.floor(second + EPSILON) + 1);
                int[] data = vertices.clone();
                float[] partBrightness = new float[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    float a = coordinate(vertices, vertex, firstAxis) < min[firstAxis] + EPSILON ? first : firstEnd;
                    float b = coordinate(vertices, vertex, secondAxis) < min[secondAxis] + EPSILON ? second : secondEnd;
                    float u = (a - min[firstAxis]) / (max[firstAxis] - min[firstAxis]);
                    float v = (b - min[secondAxis]) / (max[secondAxis] - min[secondAxis]);
                    int offset = vertex * IQuadTransformer.STRIDE;
                    data[offset + firstAxis] = Float.floatToRawIntBits(a);
                    data[offset + secondAxis] = Float.floatToRawIntBits(b);
                    float textureU = 0;
                    float textureV = 0;
                    float[] color = new float[4];
                    float blockLight = 0;
                    float skyLight = 0;
                    for (int corner = 0; corner < 4; corner++) {
                        float weight = (coordinate(vertices, corner, firstAxis) < min[firstAxis] + EPSILON ? 1 - u : u)
                            * (coordinate(vertices, corner, secondAxis) < min[secondAxis] + EPSILON ? 1 - v : v);
                        int source = corner * IQuadTransformer.STRIDE;
                        textureU += Float.intBitsToFloat(vertices[source + IQuadTransformer.UV0]) * weight;
                        textureV += Float.intBitsToFloat(vertices[source + IQuadTransformer.UV0 + 1]) * weight;
                        partBrightness[vertex] += brightness[corner] * weight;
                        for (int channel = 0; channel < 4; channel++) {
                            color[channel] += ((vertices[source + IQuadTransformer.COLOR] >>> (channel * 8)) & 255) * weight;
                        }
                        int light = vertices[source + IQuadTransformer.UV2];
                        blockLight += (light & 65535) * weight;
                        skyLight += (light >>> 16) * weight;
                    }
                    data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(textureU);
                    data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(textureV);
                    data[offset + IQuadTransformer.COLOR] = Math.round(color[0]) | Math.round(color[1]) << 8
                        | Math.round(color[2]) << 16 | Math.round(color[3]) << 24;
                    data[offset + IQuadTransformer.UV2] = Math.round(blockLight) | Math.round(skyLight) << 16;
                }
                parts.add(part(new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), quad.getSprite(),
                    quad.isShade(), quad.hasAmbientOcclusion()), partBrightness));
                second = secondEnd;
            }
            first = firstEnd;
        }
        return parts;
    }

    private static Part part(BakedQuad quad, float[] brightness) {
        float[] center = new float[3];
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) center[axis] += coordinate(quad.getVertices(), vertex, axis) * 0.25F;
        }
        Direction direction = quad.getDirection();
        // A face exactly on a boundary belongs to the cell on its inner side.
        BlockPos origin = BlockPos.containing(center[0] - direction.getStepX() * EPSILON,
            center[1] - direction.getStepY() * EPSILON, center[2] - direction.getStepZ() * EPSILON);
        return new Part(quad, origin, brightness);
    }

    private static boolean isEnd(float value, float min, float max) {
        return Math.abs(value - min) < EPSILON || Math.abs(value - max) < EPSILON;
    }

    private static float coordinate(int[] vertices, int vertex, int axis) {
        float value = Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + axis]);
        float integer = Math.round(value);
        return Math.abs(value - integer) < EPSILON ? integer : value;
    }
}
