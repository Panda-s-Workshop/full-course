package com.panda.fullcourse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.panda.fullcourse.kitchen.CuttingBoardBlock;
import com.panda.fullcourse.kitchen.CuttingBoardBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Draws what lies on a Cutting Board: the item flat on the wood, turned with the board (as vanilla's campfire does). */
public class CuttingBoardRenderer implements BlockEntityRenderer<CuttingBoardBlockEntity, CuttingBoardRenderer.State> {
    /** The board's top: 1 px up, plus half the item's 1 px thickness at the drawn scale. */
    private static final float TOP = 1 / 16F + 0.02F;
    private static final float SCALE = 0.55F;

    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public Direction facing = Direction.NORTH;
    }

    private final ItemModelResolver items;

    public CuttingBoardRenderer(BlockEntityRendererProvider.Context context) {
        items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CuttingBoardBlockEntity board, State state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(board, state, partialTick, camera, crumbling);
        state.facing = board.getBlockState().getValue(CuttingBoardBlock.FACING);
        items.updateForTopItem(state.item, board.item(), ItemDisplayContext.FIXED, board.getLevel(), null, (int) board.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5F, TOP, 0.5F);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        pose.rotateDegrees(Axis.XP, 90F);
        pose.scale(SCALE, SCALE, SCALE);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
