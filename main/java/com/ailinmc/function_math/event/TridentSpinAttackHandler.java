package com.ailinmc.function_math.event;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ailinmc.function_math.expr.ExpressionEvaluator;

/**
 * 处理激流（Riptide）三叉戟飞行的函数轨迹与粒子效果。
 */
public class TridentSpinAttackHandler {

    // -----------------------------------------------------------------------
    // 常量
    // -----------------------------------------------------------------------

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^(f\\(x\\)=|y=)(.+)$");

    private static final double PLAYER_SPEED = 0.55;

    private static final double TRAJECTORY_SCALE = 1.0;
    private static final double DELTA_X = 0.001;

    private static final double MAX_Y = 320;
    private static final double MIN_Y = -64;

    private static final double MAX_DISTANCE = 1000;
    private static final int MAX_TICKS = 30 * 20;
    private static final int SPIN_TICKS_HOLD_VALUE = 20;

    // -----------------------------------------------------------------------
    // 状态
    // -----------------------------------------------------------------------

    private static final Map<UUID, SpinAttackData> spinAttackMap = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // 反射：autoSpinAttackTicks 是 LivingEntity 的私有字段
    // -----------------------------------------------------------------------

    private static Field autoSpinAttackTicksField = null;

    private static Method setLivingEntityFlagMethod = null;

    private static Field autoSpinAttackDmgField = null;
    private static Field autoSpinAttackItemStackField = null;

    static {
        try {
            autoSpinAttackTicksField = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredField("autoSpinAttackTicks");
            autoSpinAttackTicksField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                autoSpinAttackTicksField = net.minecraft.world.entity.LivingEntity.class
                        .getDeclaredField("f_20882_");
                autoSpinAttackTicksField.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .error("[TridentSpinAttack] 无法反射 autoSpinAttackTicks 字段，激流轨迹功能不可用", ex);
            }
        }

        try {
            setLivingEntityFlagMethod = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredMethod("setLivingEntityFlag", int.class, boolean.class);
            setLivingEntityFlagMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            try {
                setLivingEntityFlagMethod = net.minecraft.world.entity.LivingEntity.class
                        .getDeclaredMethod("m_21078_", int.class, boolean.class);
                setLivingEntityFlagMethod.setAccessible(true);
            } catch (NoSuchMethodException ex) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .error("[TridentSpinAttack] 无法反射 setLivingEntityFlag 方法，"
                                + "轨迹结束后客户端冲刺动画可能无法正确清除", ex);
            }
        }

        try {
            autoSpinAttackDmgField = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredField("autoSpinAttackDmg");
            autoSpinAttackDmgField.setAccessible(true);
        } catch (NoSuchFieldException e) {
        }

        try {
            autoSpinAttackItemStackField = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredField("autoSpinAttackItemStack");
            autoSpinAttackItemStackField.setAccessible(true);
        } catch (NoSuchFieldException e) {
        }
    }

    // -----------------------------------------------------------------------
    // 注册
    // -----------------------------------------------------------------------

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new TridentSpinAttackHandler());
    }

    // -----------------------------------------------------------------------
    // 主事件处理
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // 只在服务端处理，与其他 Handler 一致
        if (player.level().isClientSide()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        UUID id = player.getUUID();
        int spinTicks = getAutoSpinAttackTicks(player);
        boolean inSpin = spinTicks > 0;

        SpinAttackData data = spinAttackMap.get(id);

        if (data == null) {
            // 玩家刚进入激流状态（本 tick 原版 travel() 已执行完毕）：尝试注册
            if (inSpin) {
                tryRegisterSpinAttack(player, id);
            }
            return;
        }

        // 玩家已在轨迹控制中
        if (!player.isAlive() || player.isRemoved()) {
            spinAttackMap.remove(id);
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .info("[TridentSpinAttack] 玩家已死亡/移除，终止轨迹接管: " + player.getName().getString());
            return;
        }

        boolean shouldStop = updatePlayerMovement(player, data, serverLevel);
        if (shouldStop) {
            endSpinAttackControl(player, id);
        } else {
            setAutoSpinAttackTicks(player, SPIN_TICKS_HOLD_VALUE);
        }
    }


    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        spinAttackMap.remove(event.getEntity().getUUID());
    }

    /**
     * 结束轨迹接管：恢复重力交还给原版下落逻辑，并将 autoSpinAttackTicks 清零
     * 让原版正常跑一次"激流结束"的收尾（例如允许再次格挡、切换武器状态等）。
     */
    private static void endSpinAttackControl(Player player, UUID id) {
        spinAttackMap.remove(id);
        player.setNoGravity(false);
        setAutoSpinAttackTicks(player, 0);
        clearAutoSpinAttackClientState(player);
        com.ailinmc.function_math.FunctionMathMod.LOGGER
                .info("[TridentSpinAttack] 轨迹接管结束，恢复重力，玩家: " + player.getName().getString());
    }

    public static void handleStopFlightMessage(IPayloadContext context) {
        Player player = context.player();
        if (player != null) {
            UUID id = player.getUUID();
            if (spinAttackMap.containsKey(id)) {
                endSpinAttackControl(player, id);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 注册逻辑
    // -----------------------------------------------------------------------

    /**
     * 检查玩家主手三叉戟是否携带函数附魔，若有则注册轨迹数据。
     */
    private void tryRegisterSpinAttack(Player player, UUID id) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() != Items.TRIDENT) return;

        // 检查附魔
        if (!hasMathFunctionEnchantment(mainHand)) return;

        // 提取表达式（优先从三叉戟自身名称，与弹射物逻辑对称）
        String displayName = mainHand.getHoverName().getString();
        String expression = extractExpression(displayName);
        if (expression.isEmpty() || !isExpressionValid(expression)) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .warn("[TridentSpinAttack] 三叉戟表达式无效: " + displayName);
            return;
        }

        // 水平方向：取玩家当前 yaw（与 TridentItem 抛出时一致）
        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        Vec3 horizontalDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();

        SpinAttackData data = new SpinAttackData(
                expression,
                player.position(),   // 以起飞位置为坐标原点
                horizontalDir,
                0.0,
                player.position()
        );
        spinAttackMap.put(id, data);

        // 完全接管物理：关闭重力，防止原版重力与我们的轨迹位移叠加产生抖动/下坠。
        player.setNoGravity(true);
        player.fallDistance = 0.0F;

        // ----- 新增：显示 ActionBar 提示 -----
        player.displayClientMessage(Component.translatable("function_math.trident.stop_action"), true);

        com.ailinmc.function_math.FunctionMathMod.LOGGER
                .info("[TridentSpinAttack] 激流函数轨迹启动: " + expression
                        + "  方向: " + horizontalDir);
    }

    // -----------------------------------------------------------------------
    // 每 tick 更新玩家位置
    // -----------------------------------------------------------------------

    /**
     * 将玩家位置推进到函数曲线上的下一个点。
     */
    private boolean updatePlayerMovement(Player player, SpinAttackData data, ServerLevel level) {

        if (data.firstTick) {
            // 第一 tick：仅初始化原点，不推进，确保从 f(0) 开始
            data.firstTick = false;
            data.projectedDistance = 0.0;
            return false;
        }

        // 超过最大 tick 数：终止并恢复重力，防止表达式长期有效导致玩家永久滞空
        data.ticksAlive++;
        if (data.ticksAlive > MAX_TICKS) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .info("[TridentSpinAttack] 超过最大持续时间，终止激流轨迹");
            return true;
        }

        data.projectedDistance += PLAYER_SPEED;

        // 超过最大水平距离：终止并恢复重力，防止表达式发散（如 f(x)=x^2）导致无限飞行
        if (data.projectedDistance > MAX_DISTANCE) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .info("[TridentSpinAttack] 超过最大飞行距离，终止激流轨迹");
            return true;
        }

        double xMath = data.projectedDistance / TRAJECTORY_SCALE;

        try {
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .warn("[TridentSpinAttack] 表达式在 x=" + xMath + " 处无效，终止");
                return true;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;
            Vec3 newPos = new Vec3(targetX, targetY, targetZ);

            // 碰撞检测：若目标位置撞到方块则终止，在碰撞点前停下并恢复重力
            if (hasSolidCollisionBetween(player, data.lastPos, newPos, level)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .info("[TridentSpinAttack] 碰撞方块，终止激流");
                return true;
            }

            // 实体碰撞检测：撞到除自己以外的实体也终止（与弹射物 Handler 逻辑对称）
            Entity hitEntity = getEntityHit(player, data.lastPos, newPos, level);
            if (hitEntity != null) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .info("[TridentSpinAttack] 碰撞实体 " + hitEntity.getName().getString() + "，终止激流");
                return true;
            }

            // 计算切线斜率，用于速度向量（让物理引擎表现正确的飞行感）
            double slope = derivative(data.expression, xMath);
            double tanX = data.lookVector.x * PLAYER_SPEED;
            double tanY = slope * PLAYER_SPEED;
            double tanZ = data.lookVector.z * PLAYER_SPEED;

            // 直接定位：teleportTo 会同步触发客户端位置修正，比单纯 setPos 更可靠。
            player.teleportTo(targetX, targetY, targetZ);

            player.setDeltaMovement(tanX, tanY, tanZ);
            player.fallDistance = 0.0F;  // 防止高处飞行触发下落伤害

            // 更新朝向，使玩家视角与曲线切线对齐（pitch 方向）
            double horizMag = Math.sqrt(tanX * tanX + tanZ * tanZ);
            if (horizMag > 1e-8 || Math.abs(tanY) > 1e-8) {
                float newPitch = (float) (-Math.toDegrees(Math.atan2(tanY, horizMag)));
                // 只修改 pitch，保留玩家 yaw 自主控制权
                player.setXRot(newPitch);
            }

            data.lastPos = newPos;

            // 粒子效果：在服务端广播，模拟弹射物尾迹
            spawnTridentParticles(player, new Vec3(tanX, tanY, tanZ), level);

        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .error("[TridentSpinAttack] 表达式计算异常", e);
            return true;
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // 实体碰撞检测
    // -----------------------------------------------------------------------

    /**
     * 检测玩家在 from -> to 移动路径上是否撞到了除自己以外的存活实体。
     * 采样步长与方块碰撞检测保持一致（0.15 格）。
     */
    private Entity getEntityHit(Player player, Vec3 from, Vec3 to, ServerLevel level) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1e-8) return null;
        Vec3 step = delta.normalize();
        int steps = (int) Math.ceil(length / 0.15);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 checkPos = from.add(step.scale(t * length));
            AABB aabb = player.getBoundingBox().move(checkPos.subtract(player.position()));

            List<Entity> candidates = level.getEntities(player, aabb,
                    e -> e != player && e.isAlive() && e instanceof LivingEntity);
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // 粒子效果
    // -----------------------------------------------------------------------

    /**
     * 在玩家身后喷出多种粒子，模拟激流三叉戟划破空气的视觉效果。
     */
    private void spawnTridentParticles(Player player, Vec3 velocity, ServerLevel level) {
        double speed = velocity.length();
        if (speed < 0.01) return;

        // 粒子生成在玩家稍后方，营造尾迹感
        Vec3 back = velocity.normalize().scale(-0.6);
        Vec3 pos = player.getEyePosition().add(back);

        double spread = 0.12;

        level.sendParticles(
                ParticleTypes.FIREWORK,
                pos.x, pos.y, pos.z,
                8,
                spread, spread, spread,
                0.0
        );

        level.sendParticles(
                ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                4,
                spread * 1.5, spread * 1.5, spread * 1.5,
                0.05
        );

        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                pos.x, pos.y, pos.z,
                3,
                spread, spread, spread,
                0.02
        );
    }

    // -----------------------------------------------------------------------
    // 碰撞检测（与 MathFunctionEnchantmentHandler 保持相同实现）
    // -----------------------------------------------------------------------

    private boolean hasSolidCollisionBetween(Player player, Vec3 from, Vec3 to, ServerLevel level) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1e-8) return false;
        Vec3 step = delta.normalize();
        int steps = (int) Math.ceil(length / 0.15);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 checkPos = from.add(step.scale(t * length));
            // 用玩家碰撞箱做检测（比弹射物大，更保守）
            AABB aabb = player.getBoundingBox().move(checkPos.subtract(player.position()));
            if (hasSolidCollision(aabb, level)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSolidCollision(AABB aabb, ServerLevel level) {
        int minX = (int) Math.floor(aabb.minX);
        int maxX = (int) Math.ceil(aabb.maxX);
        int minY = (int) Math.floor(aabb.minY);
        int maxY = (int) Math.ceil(aabb.maxY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxZ = (int) Math.ceil(aabb.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(x, y, z);
                    var state = level.getBlockState(bp);
                    if (!state.getCollisionShape(level, bp).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    private boolean hasMathFunctionEnchantment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var enchantments = stack.getEnchantments();
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            ResourceLocation key = holder.unwrapKey().map(k -> k.location()).orElse(null);
            if (key != null && key.toString().equals("function_math:math_function")) {
                return true;
            }
        }
        return false;
    }

    private String extractExpression(String displayName) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(displayName);
        return matcher.matches() ? matcher.group(2).trim() : "";
    }

    private boolean isExpressionValid(String expression) {
        try {
            double v0 = ExpressionEvaluator.evaluate(expression, 0);
            double v1 = ExpressionEvaluator.evaluate(expression, 1);
            return !Double.isNaN(v0) && !Double.isInfinite(v0)
                    && !Double.isNaN(v1) && !Double.isInfinite(v1);
        } catch (Exception e) {
            return false;
        }
    }

    private double derivative(String expression, double x) {
        double fx = ExpressionEvaluator.evaluate(expression, x);
        double fxd = ExpressionEvaluator.evaluate(expression, x + DELTA_X);
        return (fxd - fx) / DELTA_X;
    }

    /** 反射读取 autoSpinAttackTicks（不可访问时返回 0，功能降级为无效） */
    private int getAutoSpinAttackTicks(Player player) {
        if (autoSpinAttackTicksField == null) return 0;
        try {
            return autoSpinAttackTicksField.getInt(player);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    /** 反射写入 autoSpinAttackTicks 以强制结束激流状态 */
    private static void setAutoSpinAttackTicks(Player player, int value) {
        if (autoSpinAttackTicksField == null) return;
        try {
            autoSpinAttackTicksField.setInt(player, value);
        } catch (IllegalAccessException e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER
                    .warn("[TridentSpinAttack] 无法写入 autoSpinAttackTicks", e);
        }
    }

    /**
     * 清除客户端同步的"正在冲刺"标志位（entityData 第 4 位），
     * 并同步清理伤害值与关联物品堆栈，与原版 checkAutoSpinAttack() 结束时的行为一致。
     */
    private static void clearAutoSpinAttackClientState(Player player) {
        if (setLivingEntityFlagMethod != null) {
            try {
                setLivingEntityFlagMethod.invoke(player, 4, false);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER
                        .warn("[TridentSpinAttack] 无法调用 setLivingEntityFlag 清除冲刺标志位，"
                                + "客户端动作可能仍显示为冲刺状态", e);
            }
        }

        if (autoSpinAttackDmgField != null) {
            try {
                autoSpinAttackDmgField.setFloat(player, 0.0F);
            } catch (IllegalAccessException ignored) {
                // 非关键字段，忽略失败
            }
        }

        if (autoSpinAttackItemStackField != null) {
            try {
                autoSpinAttackItemStackField.set(player, null);
            } catch (IllegalAccessException ignored) {
                // 非关键字段，忽略失败
            }
        }
    }

    // -----------------------------------------------------------------------
    // 数据类
    // -----------------------------------------------------------------------

    private static class SpinAttackData {
        final String expression;
        final Vec3 origin;
        final Vec3 lookVector;
        double projectedDistance;
        Vec3 lastPos;
        boolean firstTick = true;
        int ticksAlive = 0;

        SpinAttackData(String expression, Vec3 origin, Vec3 lookVector,
                       double projectedDistance, Vec3 lastPos) {
            this.expression = expression;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = projectedDistance;
            this.lastPos = lastPos;
        }
    }
}