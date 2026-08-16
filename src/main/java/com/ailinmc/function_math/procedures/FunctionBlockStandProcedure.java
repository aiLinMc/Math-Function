package com.ailinmc.function_math.procedures;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

import com.ailinmc.function_math.FunctionMathMod;
import com.ailinmc.function_math.expr.ExpressionEvaluator;
import com.ailinmc.function_math.expr.AstNode;

import java.util.ArrayList;
import java.util.List;

public class FunctionBlockStandProcedure {
    private static final double MIN_Y = -64;
    private static final double MAX_Y = 320;
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

        // 放置告示牌
        //placeSignWithExpression(world, player, expressionStr);

        double startX = player.getX();
        double startY = player.getY();
        double startZ = player.getZ();

        float yaw = player.getYRot();
        double[] dir = getAxisDirection(yaw);
        double dx = dir[0];
        double dz = dir[1];
        FunctionMathMod.LOGGER.info("生成方向: dx=" + dx + ", dz=" + dz);

        double yMinScaled = Double.POSITIVE_INFINITY;
        double yMaxScaled = Double.NEGATIVE_INFINITY;
        double originX = 0, originZ = 0, originY = startY;
        if (coordinateSystem) {
            double step = 1.0;
            for (double x = xMin; x <= xMax + 1e-9; x += step) {
                double yValue;
                try {
                    yValue = exprNode.evaluate(x);
                    if (Double.isInfinite(yValue) || Double.isNaN(yValue)) continue;
                    double scaledY = yValue * scale;
                    if (scaledY >= MIN_Y && scaledY <= MAX_Y) {
                        if (scaledY < yMinScaled) yMinScaled = scaledY;
                        if (scaledY > yMaxScaled) yMaxScaled = scaledY;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            if (yMinScaled == Double.POSITIVE_INFINITY) {
                coordinateSystem = false;
                FunctionMathMod.LOGGER.warn("没有有效函数值，跳过坐标系生成");
                sendErrorMessage(source, "function_math.error.no_valid_points");
            } else {
                if (Math.abs(yMaxScaled - yMinScaled) < 1e-6) {
                    yMinScaled -= 1;
                    yMaxScaled += 1;
                }
                double leftX = dz;
                double leftZ = -dx;
                originX = startX + (0 - xMin) * scale * dx + leftX;
                originZ = startZ + (0 - xMin) * scale * dz + leftZ;
                originY = startY;
                generateCoordinateSystem(world, originX, originZ, originY, dx, dz, scale, xMin, xMax, yMinScaled, yMaxScaled);
            }
        }

        BlockPos prevPos = null;
        Double prevX = null;
        double step = 1.0;
        for (double x = xMin; x <= xMax + 1e-9; x += step) {
            double yValue;
            try {
                yValue = exprNode.evaluate(x);
                if (Double.isInfinite(yValue) || Double.isNaN(yValue)) {
                    FunctionMathMod.LOGGER.warn("函数值无效，跳过: x=" + x);
                    prevPos = null;
                    prevX = null;
                    continue;
                }

                double scaledY = yValue * scale;
                if (scaledY < MIN_Y || scaledY > MAX_Y) {
                    FunctionMathMod.LOGGER.warn("函数值超出高度范围，跳过: x=" + x + ", y=" + scaledY);
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
            double worldY = startY + yValue * scale;

            BlockPos currentPos = BlockPos.containing(worldX, worldY, worldZ);
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
        sendSuccessMessage(source, expressionStr, blockstate, duration, originX, originY, originZ, coordinateSystem);
        FunctionMathMod.LOGGER.info("函数图像生成完成，耗时 {} ms", duration);
    }

	/*
    private static void placeSignWithExpression(Level world, Player player, String expression) {
        BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        if (!world.getBlockState(pos).canBeReplaced()) {
            pos = pos.above();
        }
        Direction facing = player.getDirection().getOpposite();
        BlockState signState = Blocks.OAK_SIGN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        world.setBlock(pos, signState, 3);
        if (world.getBlockEntity(pos) instanceof SignBlockEntity signEntity) {
            List<String> lines = wrapText(expression, 15);
            SignText signText = signEntity.getText(true);
            for (int i = 0; i < 4; i++) {
                String line = i < lines.size() ? lines.get(i) : "";
                signText = signText.setMessage(i, Component.literal(line));
            }
            signEntity.setText(signText, false);  // 第二个参数表示是否打蜡
            signEntity.setChanged();
        }
    }
	*/

    private static List<String> wrapText(String text, int maxLen) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (current.length() >= maxLen) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (current.length() > 0) lines.add(current.toString());
        while (lines.size() > 4) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static void generateCoordinateSystem(Level world,
                                                  double originX, double originZ, double originY,
                                                  double dx, double dz, double scale,
                                                  double xMin, double xMax,
                                                  double yMin, double yMax) {
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        for (double x = xMin; x <= xMax + 1e-9; x += 1.0) {
            double offset = (x - 0) * scale;
            double worldX = originX + offset * dx;
            double worldZ = originZ + offset * dz;
            double worldY = originY;
            BlockPos pos = BlockPos.containing(worldX, worldY, worldZ);
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight()) {
                world.setBlock(pos, quartz, 3);
            }
        }
        int yMinInt = (int) Math.floor(originY + yMin);
        int yMaxInt = (int) Math.floor(originY + yMax);
        if (yMinInt > originY) yMinInt = (int) Math.floor(originY);
        if (yMaxInt < originY) yMaxInt = (int) Math.floor(originY);
        for (int y = yMinInt; y <= yMaxInt; y++) {
            BlockPos pos = new BlockPos(BlockPos.containing(originX, y, originZ));
            if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight()) {
                world.setBlock(pos, quartz, 3);
            }
        }
        BlockPos originPos = BlockPos.containing(originX, originY, originZ);
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
        //message.append(Component.translatable("function_math.result.sign"));
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
}