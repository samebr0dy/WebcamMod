package com.lichcode.webcam;

import com.lichcode.webcam.Video.PlayerVideo;
import com.lichcode.webcam.Video.PlayerVideoPacketCodec;
import com.lichcode.webcam.config.WebcamConfig;
import com.lichcode.webcam.render.PlayerFaceRenderer;

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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import static com.lichcode.webcam.WebcamMod.MOD_ID;

import java.nio.ByteBuffer;



public class WebcamModClient implements ClientModInitializer {
	public static KeyBinding openWebcamSettingsKey;

	private static NativeImageBackedTexture hudTexture;
	private static Identifier hudTextureId;

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
			// вытаскиваем свой видеопоток
			PlayerVideo myVideo = PlayerFeeds.getPlayerVideo(selfUuid);

			if (myVideo != null && myVideo.frame != null) {
				int w = myVideo.width;
				int h = myVideo.height;
				ByteBuffer buf = myVideo.asByteBuffer(); // rgb

				// RGB → NativeImage (полный ARGB, alpha=255)
				NativeImage nativeImg = new NativeImage(w, h, false);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int r = buf.get() & 0xFF;
						int g = buf.get() & 0xFF;
						int b = buf.get() & 0xFF;
						nativeImg.setColor(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
					}
				}

				// (пере)создаём динамическую текстуру, если нужно
				if (hudTexture == null || hudTexture.getImage().getWidth() != w || hudTexture.getImage().getHeight() != h) {
					if (hudTextureId != null) {
						MinecraftClient.getInstance().getTextureManager().destroyTexture(hudTextureId);
					}
					hudTexture = new NativeImageBackedTexture(nativeImg);
					hudTextureId = MinecraftClient.getInstance()
						.getTextureManager()
						.registerDynamicTexture("webcam_hud", hudTexture);
				} else {
					hudTexture.setImage(nativeImg);
					hudTexture.upload();
				}

				// рисуем превью 64×64 в правом верхнем углу
				int size = 32;
				int xPos = MinecraftClient.getInstance().getWindow().getScaledWidth() - size - 8;
				int yPos = 8;

				ctx.drawTexture(hudTextureId, xPos, yPos, 0, 0, size, size, size, size);
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
