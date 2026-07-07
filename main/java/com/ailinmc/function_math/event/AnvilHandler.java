package com.ailinmc.function_math.event;

import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;

import com.ailinmc.function_math.expr.ExpressionEvaluator;

public class AnvilHandler {

    private static final ResourceLocation MATH_FUNCTION_ID = ResourceLocation.fromNamespaceAndPath("function_math", "math_function");

    public static void init() {
        NeoForge.EVENT_BUS.register(new AnvilHandler());
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        String name = event.getName();

        if (left.isEmpty()) return;

        if (isFunctionEnchantmentBook(right)) {
            event.setCost(0);
            return;
        }

        if (name != null && !name.isEmpty() && isValidFunctionExpression(name)) {
            event.setCost(0);
        }
    }

    private static boolean isFunctionEnchantmentBook(ItemStack stack) {
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

    private static boolean isValidFunctionExpression(String expression) {
        try {
            ExpressionEvaluator.evaluate(expression, 0.0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}