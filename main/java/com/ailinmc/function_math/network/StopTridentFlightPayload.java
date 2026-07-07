package com.ailinmc.function_math.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.ailinmc.function_math.FunctionMathMod;

public record StopTridentFlightPayload() implements CustomPacketPayload {
    public static final Type<StopTridentFlightPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(FunctionMathMod.MODID, "stop_trident_flight"));

    public static final StreamCodec<FriendlyByteBuf, StopTridentFlightPayload> CODEC = 
        StreamCodec.unit(new StopTridentFlightPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}