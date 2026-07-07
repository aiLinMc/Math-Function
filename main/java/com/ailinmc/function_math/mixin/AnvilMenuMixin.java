package com.ailinmc.function_math.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;

import com.ailinmc.function_math.expr.ExpressionEvaluator;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    private static final ResourceLocation MATH_FUNCTION_ID = ResourceLocation.fromNamespaceAndPath("function_math", "math_function");

    @Shadow
    private String itemName;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void onCreateResult(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        
        ItemStack left = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack right = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
        
        if (left.isEmpty()) return;
        
        if (isFunctionEnchantmentBook(right)) {
            menu.setMaximumCost(0);
            return;
        }
        
        if (itemName != null && !itemName.isEmpty()) {
            String cleanExpr = stripExpressionPrefix(itemName);
            if (isValidFunctionExpression(cleanExpr)) {
                menu.setMaximumCost(0);
            }
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void onMayPickup(Player player, boolean unused, CallbackInfoReturnable<Boolean> ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        if (menu.getCost() == 0 && menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem()) {
            ci.setReturnValue(true);
        }
    }

    private boolean isFunctionEnchantmentBook(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return false;
        
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            ResourceLocation id = holder.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
            if (id != null && id.equals(MATH_FUNCTION_ID)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidFunctionExpression(String expression) {
        if (expression == null || expression.isEmpty()) return false;
        try {
            ExpressionEvaluator.evaluate(expression, 0.0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String stripExpressionPrefix(String expression) {
        String s = expression.trim();
        if (s.startsWith("y=") || s.startsWith("Y=")) {
            return s.substring(2).trim();
        }
        if (s.startsWith("f(x)=") || s.startsWith("F(X)=")) {
            return s.substring(5).trim();
        }
        if (s.startsWith("f(")) {
            int eqIdx = s.indexOf('=');
            if (eqIdx > 0) {
                return s.substring(eqIdx + 1).trim();
            }
        }
        return s;
    }
}