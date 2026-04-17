package com.ailinmc.function_math.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;

import com.ailinmc.function_math.mixin.AnvilScreenMixin;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @ModifyConstant(method = "subInit", constant = @Constant(intValue = 50))
    private int changeNameLengthLimit(int original) {
        return 500;
    }
}