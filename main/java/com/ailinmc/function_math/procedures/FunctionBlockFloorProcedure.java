package com.ailinmc.function_math.procedures;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

import com.ailinmc.function_math.FunctionMathMod;
import com.ailinmc.function_math.expr.ExpressionEvaluator;
import com.ailinmc.function_math.expr.AstNode;

import net.minecraft.world.level.LevelAccessor;

public class FunctionBlockFloorProcedure {
    private static final double MIN_VALUE = -64;
    private static final double MAX_VALUE = 320;
    private static final int MAX_SPAN = 256;

    public static void execute(CommandContext<CommandSourceStack> arguments) {
        long startTime = System.currentTimeMillis();

        String expressionStr = StringArgumentType.getString(arguments, "analyticExpression");
        double xMin = DoubleArgumentType.getDouble(arguments, "functionValueMin");
        double xMax = DoubleArgumentType.getDouble(arguments, "functionValueMax");
        BlockState blockstate = BlockStateArgument.getBlock(arguments, "block").getState();

        double scale = 1.0;
        try {
            scale = DoubleArgumentType.getDouble(arguments, "scale");
        } catch (IllegalArgumentException ignored) {}

        boolean coordinateSystem = false;
        try {
            coordinateSystem = BoolArgumentType.getBool(arguments, "coordinateSystem");
        } catch (IllegalArgumentException ignored) {}

        FunctionMathMod.LOGGER.info("表达式: " + expressionStr + ", 定义域: [" + xMin + ", " + xMax + "], 缩放: " + scale + ", 坐标系: " + coordinateSystem);

        AstNode exprNode;
        try {
            exprNode = ExpressionEvaluator.parse(expressionStr);
        } catch (Exception e) {
            FunctionMathMod.LOGGER.error("表达式解析失败: " + e.getMessage());
            sendErrorMessage(arguments.getSource(), "function_math.error.parse_failed", e.getMessage());
            return;
        }

        var source = arguments.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            FunctionMathMod.LOGGER.error("命令必须由玩家执行");
            sendErrorMessage(source, "function_math.error.no_player");
            return;
        }
        Level world = source.getLevel();
        if (world == null) {
            FunctionMathMod.LOGGER.error("无法获取世界");
            sendErrorMessage(source, "function_math.error.no_world");
            return;
        }

        double startX = player.getX();
        double startY = player.getY();
        double startZ = player.getZ();

        float yaw = player.getYRot();
        double[] dir = getAxisDirection(yaw);
        double dx = dir[0];
        double dz = dir[1];
        double leftX = dz;
        double leftZ = -dx;
        FunctionMathMod.LOGGER.info("生成方向: dx=" + dx + ", dz=" + dz + " | 左侧方向: leftX=" + leftX + ", leftZ=" + leftZ);

        double fMinScaled = Double.POSITIVE_INFINITY;
        double fMaxScaled = Double.NEGATIVE_INFINITY;
        double originX = 0, originZ = 0;
        if (coordinateSystem) {
            double step = 1.0;
            for (double x = xMin; x <= xMax + 1e-9; x += step) {
                double fValue;
                try {
                    fValue = exprNode.evaluate(x);
                    if (Double.isInfinite(fValue) || Double.isNaN(fValue)) continue;
                    double scaledF = fValue * scale;
                    if (scaledF >= MIN_VALUE && scaledF <= MAX_VALUE) {
                        if (scaledF < fMinScaled) fMinScaled = scaledF;
                        if (scaledF > fMaxScaled) fMaxScaled = scaledF;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (fMinScaled == Double.POSITIVE_INFINITY) {
                coordinateSystem = false;
                FunctionMathMod.LOGGER.warn("没有有效函数值，跳过坐标系生成");
                sendErrorMessage(source, "function_math.error.no_valid_points");
            } else {
                if (Math.abs(fMaxScaled - fMinScaled) < 1e-6) {
                    fMinScaled -= 1;
                    fMaxScaled += 1;
                }
                originX = startX + (0 - xMin) * scale * dx;
                originZ = startZ + (0 - xMin) * scale * dz;
                double coordinateY = startY - 1;
                generateCoordinateSystem(world, originX, originZ, coordinateY, dx, dz, leftX, leftZ, scale, xMin, xMax, fMinScaled, fMaxScaled);
            }
        }

        BlockPos prevPos = null;
        Double prevX = null;
        double step = 1.0;
        for (double x = xMin; x <= xMax + 1e-9; x += step) {
            double fValue;
            try {
                fValue = exprNode.evaluate(x);
                if (Double.isInfinite(fValue) || Double.isNaN(fValue)) {
                    FunctionMathMod.LOGGER.warn("函数值无效，跳过: x=" + x);
                    prevPos = null;
                    prevX = null;
                    continue;
                }

                double scaledF = fValue * scale;
                if (scaledF < MIN_VALUE || scaledF > MAX_VALUE) {
                    FunctionMathMod.LOGGER.warn("函数值超出范围，跳过: x=" + x + ", f(x)=" + scaledF);
                    prevPos = null;
                    prevX = null;
                    continue;
                }
            } catch (Exception e) {
                FunctionMathMod.LOGGER.error("计算错误 x=" + x + " : " + e.getMessage());
                prevPos = null;
                prevX = null;
                continue;
            }

            double offset = (x - xMin) * scale;
            double worldX = startX + offset * dx;
            double worldZ = startZ + offset * dz;
            double fOffsetX = fValue * scale * leftX;
            double fOffsetZ = fValue * scale * leftZ;
            BlockPos currentPos = BlockPos.containing(worldX + fOffsetX, startY, worldZ + fOffsetZ);
            placeBlock(world, currentPos, blockstate);

            if (prevPos != null && prevX != null && Math.abs(x - prevX - step) < 1e-9) {
                drawLine(world, prevPos, currentPos, blockstate);
            } else if (prevPos != null) {
                FunctionMathMod.LOGGER.info("检测到不连续点：x 从 " + prevX + " 跳到 " + x + "，跳过连线");
            }

            prevPos = currentPos;
            prevX = x;
        }

        long duration = System.currentTimeMillis() - startTime;
        sendSuccessMessage(source, expressionStr, blockstate, duration, originX, startY - 1, originZ, coordinateSystem);
        FunctionMathMod.LOGGER.info("函数图像生成完成，耗时 {} ms", duration);
    }


    private static void generateCoordinateSystem(Level world,
                                                  double originX, double originZ, double yLevel,
                                                  double dx, double dz, double leftX, double leftZ,
                                                  double scale, double xMin, double xMax,
                                                  double fMin, double fMax) {
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();

        // 绘制 X 轴
        for (double x = xMin; x <= xMax + 1e-9; x += 1.0) {
            double offset = (x - 0) * scale;
            double worldX = originX + offset * dx;
            double worldZ = originZ + offset * dz;
            BlockPos pos = BlockPos.containing(worldX, yLevel, worldZ);
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight()) {
                world.setBlock(pos, quartz, 3);
            }
        }

        // 绘制 Y 轴
        for (double f = fMin; f <= fMax + 1e-9; f += 1.0) {
            double offsetF = f * scale;
            double worldX = originX + offsetF * leftX;
            double worldZ = originZ + offsetF * leftZ;
            BlockPos pos = BlockPos.containing(worldX, yLevel, worldZ);
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight()) {
                world.setBlock(pos, quartz, 3);
            }
        }

        // 原点标记
        BlockPos originPos = BlockPos.containing(originX, yLevel, originZ);
        if (originPos.getY() >= world.getMinBuildHeight() && originPos.getY() <= world.getMaxBuildHeight()) {
            world.setBlock(originPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
    }

    private static void sendSuccessMessage(CommandSourceStack source,
                                           String expression, BlockState blockstate,
                                           long duration, double originX, double originY, double originZ,
                                           boolean hasCoordinateSystem) {
        MutableComponent message = Component.translatable("function_math.result.success");
        message.append("\n");
        message.append(Component.translatable("function_math.result.expression", expression)).append("\n");
        message.append(Component.translatable("function_math.result.block", blockstate.getBlock().getName())).append("\n");
        message.append(Component.translatable("function_math.result.duration", duration)).append("\n");
        if (hasCoordinateSystem) {
            message.append(Component.translatable("function_math.result.origin",
                    String.format("%.1f", originX),
                    String.format("%.1f", originY),
                    String.format("%.1f", originZ))).append("\n");
        }
        source.sendSuccess(() -> message, false);
    }

    private static void sendErrorMessage(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
    }

    private static double[] getAxisDirection(float yaw) {
        float angle = yaw % 360;
        if (angle < 0) angle += 360;
        float[][] dirs = {
            {0,  0,  1},
            {90,  -1, 0},
            {180, 0,  -1},
            {270, 1,  0}
        };
        double minDiff = 180;
        double[] best = {0, 0};
        for (float[] d : dirs) {
            float diff = Math.abs(angle - d[0]);
            diff = Math.min(diff, 360 - diff);
            if (diff < minDiff) {
                minDiff = diff;
                best[0] = d[1];
                best[1] = d[2];
            }
        }
        return best;
    }

    private static void placeBlock(Level world, BlockPos pos, BlockState blockstate) {
        if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight()) {
            world.setBlock(pos, blockstate, 3);
        } else {
            FunctionMathMod.LOGGER.warn("超出世界高度范围，跳过: y=" + pos.getY());
        }
    }

    private static void drawLine(Level world, BlockPos from, BlockPos to, BlockState blockstate) {
        int x0 = from.getX();
        int y0 = from.getY();
        int z0 = from.getZ();
        int x1 = to.getX();
        int y1 = to.getY();
        int z1 = to.getZ();

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;

        int maxStep = Math.max(Math.max(dx, dy), dz);
        if (maxStep > MAX_SPAN) {
            FunctionMathMod.LOGGER.warn("两点距离过大，跳过连线: " + from + " -> " + to);
            return;
        }

        int err = maxStep / 2;
        int x = x0, y = y0, z = z0;
        for (int i = 0; i <= maxStep; i++) {
            BlockPos pos = new BlockPos(x, y, z);
            placeBlock(world, pos, blockstate);

            err -= dx;
            if (err < 0) { err += maxStep; x += sx; }
            err -= dy;
            if (err < 0) { err += maxStep; y += sy; }
            err -= dz;
            if (err < 0) { err += maxStep; z += sz; }
        }
    }

    public static void execute(LevelAccessor world, double x, double y, double z, CommandContext<CommandSourceStack> arguments) {
        execute(arguments);
    }
}