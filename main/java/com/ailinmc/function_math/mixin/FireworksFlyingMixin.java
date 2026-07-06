package com.ailinmc.function_math.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

import com.ailinmc.function_math.event.MathFunctionEnchantmentHandler;


//拦截 FireworkRocketEntity.tick() 中的原版飞行逻辑。

@Mixin(FireworkRocketEntity.class)
public abstract class FireworksFlyingMixin {

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 0
        )
    )
    private void onBeforeVelocityAccumulate(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (MathFunctionEnchantmentHandler.isFunctionRocket(self)) {
            self.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 0
        )
    )
    private void redirectMove(FireworkRocketEntity self, MoverType moverType, Vec3 movement) {
        if (MathFunctionEnchantmentHandler.isFunctionRocket(self)) {
            return;
        }
        // 普通烟花火箭走原版逻辑
        self.move(moverType, movement);
    }
}