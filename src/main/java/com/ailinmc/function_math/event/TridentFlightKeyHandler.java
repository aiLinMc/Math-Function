package com.ailinmc.function_math.event;

import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;

import com.ailinmc.function_math.network.StopTridentFlightPayload;

public class TridentFlightKeyHandler {

    private static boolean jumpKeyPressed = false;

    public static void initClient(IEventBus modEventBus) {
        modEventBus.addListener(TridentFlightKeyHandler::onClientSetup);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new TridentFlightKeyHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        KeyMapping jumpKey = Minecraft.getInstance().options.keyJump;
        boolean isJumpKey = jumpKey.matches(event.getKey(), event.getScanCode());

        if (isJumpKey && event.getAction() == 1) {
            if (!jumpKeyPressed) {
                jumpKeyPressed = true;
                sendStopFlightMessage(player);
            }
        } else if (isJumpKey && event.getAction() == 0) {
            jumpKeyPressed = false;
        }
    }

    private void sendStopFlightMessage(LocalPlayer player) {
        if (player.connection != null) {
            player.connection.send(new StopTridentFlightPayload());
        }
    }
}