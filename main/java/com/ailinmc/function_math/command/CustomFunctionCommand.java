package com.ailinmc.function_math.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.ailinmc.function_math.expr.CustomFunctions;
import com.ailinmc.function_math.expr.ExpressionEvaluator;
import com.ailinmc.function_math.expr.RecursionException;

@EventBusSubscriber
public class CustomFunctionCommand {
    private static final SuggestionProvider<CommandSourceStack> FUNCTION_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();
        for (String name : CustomFunctions.getAllFunctions().keySet()) {
            if (name.toLowerCase().startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("customfunction").requires(s -> s.hasPermission(0))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String expression = StringArgumentType.getString(ctx, "expression");

                                                    if (CustomFunctions.isReserved(name)) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.add.reserved", name));
                                                        return 0;
                                                    }

                                                    if (!name.matches("^[a-zA-Z][a-zA-Z0-9]*$")) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.add.invalid_name"));
                                                        return 0;
                                                    }

                                                    if (CustomFunctions.exists(name)) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.add.exists", name));
                                                        return 0;
                                                    }

                                                    try {
                                                        ExpressionEvaluator.parse(expression);
                                                        boolean success = CustomFunctions.addFunction(name, expression);
                                                        if (success) {
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.add.success", name, expression), false);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.add.failed"));
                                                        }
                                                    } catch (Exception e) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.invalid_expression", e.getMessage()));
                                                    }
                                                    return 0;
                                                }))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(FUNCTION_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String expr = CustomFunctions.getFunction(name);
                                            if (expr != null) {
                                                ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.get.success", name, expr), false);
                                            } else {
                                                ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.not_found", name));
                                            }
                                            return 0;
                                        })))
                        .then(Commands.literal("eval")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(FUNCTION_SUGGESTIONS)
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    double x = DoubleArgumentType.getDouble(ctx, "x");
                                                    try {
                                                        double result = CustomFunctions.evaluate(name, x);
                                                        if (!Double.isNaN(result)) {
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.eval.success", name, x, result), false);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.not_found", name));
                                                        }
                                                    } catch (RecursionException e) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.recursion"));
                                                    }
                                                    return 0;
                                                }))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(FUNCTION_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean removed = CustomFunctions.removeFunction(name);
                                            if (removed) {
                                                ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.remove.success", name), false);
                                            } else {
                                                ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.not_found", name));
                                            }
                                            return 0;
                                        })))
                        .then(Commands.literal("remove-all")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    if (CustomFunctions.getAllFunctions().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.remove_all.empty"), false);
                                    } else {
                                        CustomFunctions.clearAllFunctions();
                                        ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.remove_all.success"), false);
                                    }
                                    return 0;
                                }))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var functions = CustomFunctions.getAllFunctions();
                                    if (functions.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.list.empty"), false);
                                    } else {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.list.title"), false);
                                        for (var entry : functions.entrySet()) {
                                            ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.list.item", entry.getKey(), entry.getValue()), false);
                                        }
                                    }
                                    return 0;
                                }))
                        .then(Commands.literal("modify")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(FUNCTION_SUGGESTIONS)
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String expression = StringArgumentType.getString(ctx, "expression");

                                                    try {
                                                        ExpressionEvaluator.parse(expression);
                                                        boolean success = CustomFunctions.modifyFunction(name, expression);
                                                        if (success) {
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("function_math.command.customfunction.modify.success", name, expression), false);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.not_found", name));
                                                        }
                                                    } catch (Exception e) {
                                                        ctx.getSource().sendFailure(Component.translatable("function_math.command.customfunction.error.invalid_expression", e.getMessage()));
                                                    }
                                                    return 0;
                                                })))));
    }
}
