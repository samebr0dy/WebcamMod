package com.lichcode.webcam.render;

import com.lichcode.webcam.PlayerFeeds;
import com.lichcode.webcam.render.image.RenderableImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL33.*;

public class PlayerFaceRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>  {

    public PlayerFaceRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        ClientPlayNetworkHandler clientPlayNetworkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (clientPlayNetworkHandler == null) {
            return;
        }
        PlayerListEntry playerListEntry = clientPlayNetworkHandler.getPlayerListEntry(state.name);
        if (playerListEntry == null) {
            return;
        }

        String playerUUID = playerListEntry.getProfile().getId().toString();
        // Get the renderable image that represents the current video frame
        // if it is null, then we haven't received any video from them so we don't attempt to render
        RenderableImage image = PlayerFeeds.get(playerUUID);
        if (image == null) {
            return;
        }

        matrices.push();

        ModelPart head = getContextModel().head;
        head.rotate(matrices);

        matrices.translate(0, 0, -0.30);
        matrices.scale(0.25f, 0.5f, 1f);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = new Matrix4f(entry.getPositionMatrix());
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE);


        buffer.vertex(position, 1, -1, 0).texture(0, 0);
        buffer.vertex(position, 1, 0, 0).texture(0, 1);
        buffer.vertex(position, -1, 0, 0).texture(1, 1);

        buffer.vertex(position, -1, 0, 0).texture(1, 1);
        buffer.vertex(position, 1, -1, 0).texture(0, 0);
        buffer.vertex(position, -1, -1, 0).texture(1, 0);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        image.init();
        RenderSystem.setShaderTexture(0, image.id);
        // Set defaults because minecraft might change this during rendering
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4); // Default is 4
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);

        // Upload new image to texture from buffer
        glBindTexture(GL_TEXTURE_2D, image.id);
        image.buffer.bind();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, image.width, image.height, GL_RGB, GL_UNSIGNED_BYTE, 0);
        image.buffer.unbind();

        image.buffer.writeAndSwap(image.data().duplicate());
        glBindTexture(GL_TEXTURE_2D, image.id);

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        glUseProgram(0);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glBindTexture(GL_TEXTURE_2D, 0);

        matrices.pop();
    }
}
