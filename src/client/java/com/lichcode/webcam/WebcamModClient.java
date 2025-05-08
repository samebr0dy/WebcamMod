package com.lichcode.webcam;

import com.lichcode.webcam.render.PlayerFaceRenderer;

import com.lichcode.webcam.video.VideoManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;

import net.minecraft.client.render.entity.PlayerEntityRenderer;


public class WebcamModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer) {
				registrationHelper.register(new PlayerFaceRenderer((PlayerEntityRenderer) entityRenderer));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			VideoManager.startCameraLoop();
		});

		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> {
			VideoManager.stopThread();
		}));

		ClientPlayNetworking.registerGlobalReceiver(VideoFramePayload.ID, ((payload, context) -> {
			PlayerFeeds.update(payload.video());
		}));
	}
}
