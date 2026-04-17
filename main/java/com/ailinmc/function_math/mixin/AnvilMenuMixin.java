package com.ailinmc.function_math.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.util.StringUtil;

import com.ailinmc.function_math.mixin.AnvilMenuMixin;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Overwrite
    private static String validateName(String name) {
        String filtered = StringUtil.filterText(name);
        return filtered;
    }
}