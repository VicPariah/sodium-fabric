package me.jellysquid.mods.sodium.mixin.features.render.entity.shadows;

import me.jellysquid.mods.sodium.client.render.vertex.VertexConsumerUtils;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.Chunk;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Unique
    private static final int SHADOW_COLOR = ColorABGR.pack(1.0f, 1.0f, 1.0f);

    /**
     * @author JellySquid
     * @reason Reduce vertex assembly overhead for shadow rendering
     */
    @Inject(method = "renderShadowPart", at = @At("HEAD"), cancellable = true)
    private static void renderShadowPartFast(MatrixStack.Entry entry, VertexConsumer vertices, Chunk chunk, WorldView world, BlockPos pos, double x, double y, double z, float radius, float opacity, CallbackInfo ci) {
        var writer = VertexConsumerUtils.convertOrLog(vertices);

        if (writer == null) {
            return;
        }

        ci.cancel();

        BlockPos blockPos = pos.down();
        BlockState blockState = world.getBlockState(blockPos);

        if (blockState.getRenderType() == BlockRenderType.INVISIBLE || !blockState.isFullCube(world, blockPos)) {
            return;
        }

        var light = world.getLightLevel(pos);

        if (light <= 3) {
            return;
        }

        VoxelShape voxelShape = blockState.getOutlineShape(world, blockPos);

        if (voxelShape.isEmpty()) {
            return;
        }

        float brightness = LightmapTextureManager.getBrightness(world.getDimension(), light);
        float alpha = (float) (((double) opacity - ((y - (double) pos.getY()) / 2.0)) * 0.5 * (double) brightness);

        if (alpha >= 0.0F) {
            if (alpha > 1.0F) {
                alpha = 1.0F;
            }

            Box box = voxelShape.getBoundingBox();

            float minX = (float) ((pos.getX() + box.minX) - x);
            float maxX = (float) ((pos.getX() + box.maxX) - x);

            float minY = (float) ((pos.getY() + box.minY) - y);

            float minZ = (float) ((pos.getZ() + box.minZ) - z);
            float maxZ = (float) ((pos.getZ() + box.maxZ) - z);

            renderShadowPart(entry, writer, radius, alpha, minX, maxX, minY, minZ, maxZ);
        }
    }

    @Unique
    private static void renderShadowPart(MatrixStack.Entry matrices, VertexBufferWriter writer, float radius, float alpha, float minX, float maxX, float minY, float minZ, float maxZ) {
        float size = 0.5F * (1.0F / radius);

        float u1 = (-minX * size) + 0.5F;
        float u2 = (-maxX * size) + 0.5F;

        float v1 = (-minZ * size) + 0.5F;
        float v2 = (-maxZ * size) + 0.5F;

        var matNormal = matrices.getNormalMatrix();
        var matPosition = matrices.getPositionMatrix();

        var color = ColorABGR.withAlpha(SHADOW_COLOR, alpha);
        var normal = MatrixHelper.transformNormal(matNormal, matrices.canSkipNormalization, Direction.UP);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ModelVertex.STRIDE);
            long ptr = buffer;

            writeShadowVertex(ptr, matPosition, minX, minY, minZ, u1, v1, color, normal);
            ptr += ModelVertex.STRIDE;

            writeShadowVertex(ptr, matPosition, minX, minY, maxZ, u1, v2, color, normal);
            ptr += ModelVertex.STRIDE;

            writeShadowVertex(ptr, matPosition, maxX, minY, maxZ, u2, v2, color, normal);
            ptr += ModelVertex.STRIDE;

            writeShadowVertex(ptr, matPosition, maxX, minY, minZ, u2, v1, color, normal);
            ptr += ModelVertex.STRIDE;

            writer.push(stack, buffer, 4, ModelVertex.FORMAT);
        }
    }

    @Unique
    private static void writeShadowVertex(long ptr, Matrix4f matPosition, float x, float y, float z, float u, float v, int color, int normal) {
        // The transformed position vector
        float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
        float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
        float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

        ModelVertex.write(ptr, xt, yt, zt, color, u, v, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, normal);
    }
}
