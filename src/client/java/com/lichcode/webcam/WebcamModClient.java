package com.lichcode.webcam;

import com.lichcode.webcam.Video.PlayerVideo;
import com.lichcode.webcam.Video.PlayerVideoPacketCodec;
import com.lichcode.webcam.config.WebcamConfig;
import com.lichcode.webcam.render.PlayerFaceRenderer;
import com.lichcode.webcam.PlayerFeeds;
import com.lichcode.webcam.render.image.RenderableImage;

import com.lichcode.webcam.screen.SettingsScreen;
import com.lichcode.webcam.video.VideoManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import static org.lwjgl.opengl.GL33.*;

import static com.lichcode.webcam.WebcamMod.MOD_ID;




public class WebcamModClient implements ClientModInitializer {
	public static KeyBinding openWebcamSettingsKey;


	@Override
	public void onInitializeClient() {
		WebcamConfig.init();
		registerSettingsCommand();

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer) {
				registrationHelper.register(new PlayerFaceRenderer((PlayerEntityRenderer) entityRenderer));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (WebcamConfig.isCameraEnabled()) {
				VideoManager.startCameraLoop();
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> {
			VideoManager.stopThread();
		}));

        ClientPlayNetworking.registerGlobalReceiver(VideoFramePayload.VIDEO_FRAME_PAYLOAD_ID, (minecraftClient, clientPlayNetworkHandler, packetByteBuf, packetSender) -> {
			packetByteBuf.resetReaderIndex();
			PlayerVideo playerVideo = PlayerVideoPacketCodec.PACKET_CODEC.decode(packetByteBuf);
			PlayerFeeds.update(playerVideo);
        });

		ClientPlayNetworking.registerGlobalReceiver(DisableCameraPayload.DISABLE_CAMERA_PAYLOAD_ID, (minecraftClient, clientPlayNetworkHandler, packetByteBuf, packetSender) -> {
			packetByteBuf.resetReaderIndex();
			String playerUUID = packetByteBuf.readString();
			PlayerFeeds.remove(playerUUID);
        });

		openWebcamSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.webcammod.opensettings",
			GLFW.GLFW_KEY_K, // Например, K, можешь поменять на любой
			"category.webcammod"
		));

		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openWebcamSettingsKey.wasPressed()) {
				client.execute(() -> client.setScreen(new com.lichcode.webcam.screen.SettingsScreen()));
			}
		});

		HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
			// UUID текущего игрока
			String selfUuid = MinecraftClient.getInstance().player.getUuidAsString();
                        // вытаскиваем предварительно подготовленное изображение
                        RenderableImage image = PlayerFeeds.get(selfUuid);

                        if (image != null && image.data() != null) {
                                image.init();

                                glBindTexture(GL_TEXTURE_2D, image.id);
                                image.buffer.bind();
                                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, image.width, image.height, GL_RGB, GL_UNSIGNED_BYTE, 0);
                                image.buffer.unbind();
                                image.buffer.writeAndSwap(image.data().duplicate());
                                glBindTexture(GL_TEXTURE_2D, image.id);

                                int size = 32;
                                int xPos = MinecraftClient.getInstance().getWindow().getScaledWidth() - size - 8;
                                int yPos = 8;

                                Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
                                Tessellator tessellator = Tessellator.getInstance();
                                BufferBuilder buffer = tessellator.getBuffer();
                                buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_TEXTURE);
                                buffer.vertex(matrix, xPos, yPos + size, 0).texture(0, 1).next();
                                buffer.vertex(matrix, xPos, yPos, 0).texture(0, 0).next();
                                buffer.vertex(matrix, xPos + size, yPos + size, 0).texture(1, 1).next();
                                buffer.vertex(matrix, xPos + size, yPos, 0).texture(1, 0).next();

                                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                                RenderSystem.setShaderColor(1, 1, 1, 1);
                                RenderSystem.setShaderTexture(0, image.id);
                                glDisable(GL_CULL_FACE);
                                tessellator.draw();
                                glEnable(GL_CULL_FACE);
                                glBindTexture(GL_TEXTURE_2D, 0);
                        }
                });
        }


	private void registerSettingsCommand() {
		ClientCommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess) -> {
			commandDispatcher.register(ClientCommandManager.literal("webcam-settings").executes(context ->  {
				MinecraftClient client = context.getSource().getClient();

				client.send(() -> client.setScreen(new SettingsScreen()));
				return 1;
			}));
		}));
	}

	public static void sendDisableCameraPayload() {
		PacketByteBuf buf = PacketByteBufs.create();
		String uuid = MinecraftClient.getInstance().player.getUuid().toString();
		buf.writeString(uuid);
		buf.resetReaderIndex();
		ClientPlayNetworking.send(DisableCameraPayload.DISABLE_CAMERA_PAYLOAD_ID, buf);
	}
}
