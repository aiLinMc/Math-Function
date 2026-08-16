package com.ailinmc.function_math.event;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ailinmc.function_math.expr.ExpressionEvaluator;

public class MathFunctionEnchantmentHandler {
    private static final Map<Projectile, ProjectileData> projectileDataMap = new ConcurrentHashMap<>();
    private static final Map<PrimedTnt, TNTData> tntDataMap = new ConcurrentHashMap<>();
    private static final Map<FireworkRocketEntity, RocketData> rocketDataMap = new ConcurrentHashMap<>();  // 新增

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^(f\\(x\\)=|y=)(.+)$");
    private static final Pattern DUAL_EXPRESSION_PATTERN = Pattern.compile("^y=(.+?);z=(.+)$");
    private static final Pattern Z_ONLY_PATTERN = Pattern.compile("^z=(.+)$");
    private static final double HORIZONTAL_SPEED = 0.3;
    private static final double MAX_DISTANCE = 1000;
    private static final int MAX_TICKS = 30 * 20;
    private static final double MAX_Y = 320;
    private static final double MIN_Y = -64;
    private static final double MAX_VERTICAL_SPEED = 2.5;
    private static final double MULTISHOT_OFFSET = 0.4;
    private static final int TNT_MAX_TICKS = 300;
    private static final double TNT_MAX_DISTANCE = 800.0;
    private static final double DELTA_X_FOR_DERIVATIVE = 0.001;
    private static final double TRAJECTORY_SCALE = 1.0;

    private static Method getPickupItemMethod = null;
    private static Method onHitEntityMethod = null;
    // 反射调用 FireworkRocketEntity 私有方法 dealExplosionDamage()，
    // 完整复现原版爆炸的范围伤害逻辑。
    private static Method dealExplosionDamageMethod = null;

    static {
        try {
            getPickupItemMethod = AbstractArrow.class.getDeclaredMethod("getPickupItem");
            getPickupItemMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            try {
                getPickupItemMethod = AbstractArrow.class.getDeclaredMethod("getPickupItemStack");
                getPickupItemMethod.setAccessible(true);
            } catch (NoSuchMethodException ex) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("无法找到获取箭物品的方法", ex);
            }
        }

        // 反射获取 dealExplosionDamage()：原版 explode() 的伤害子方法，private void，无参数
        try {
            dealExplosionDamageMethod = FireworkRocketEntity.class.getDeclaredMethod("dealExplosionDamage");
            dealExplosionDamageMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("无法找到 FireworkRocketEntity.dealExplosionDamage()，碰撞爆炸将无范围伤害", e);
        }

        // 反射获取 AbstractArrow.onHitEntity()：处理实体碰撞伤害
        try {
            onHitEntityMethod = AbstractArrow.class.getDeclaredMethod("onHitEntity", 
                net.minecraft.world.phys.EntityHitResult.class);
            onHitEntityMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            try {
                onHitEntityMethod = AbstractArrow.class.getDeclaredMethod("m_81162_", 
                    net.minecraft.world.phys.EntityHitResult.class);
                onHitEntityMethod.setAccessible(true);
            } catch (NoSuchMethodException ex) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("无法找到 AbstractArrow.onHitEntity()，碰撞实体将无伤害", ex);
            }
        }
    }

    /**
     * 将烟花火箭移动到指定位置后立即引爆，完整复现原版 explode() 的逻辑：
     */
    private void explodeRocket(FireworkRocketEntity rocket, Vec3 explosionPos) {
        if (rocket.isRemoved()) return;

        rocket.setPos(explosionPos.x, explosionPos.y, explosionPos.z);
        rocket.level().broadcastEntityEvent(rocket, (byte) 17);

        // 施加范围伤害（原版 dealExplosionDamage：
        if (dealExplosionDamageMethod != null) {
            try {
                dealExplosionDamageMethod.invoke(rocket);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("调用 dealExplosionDamage() 失败", e);
            }
        }

        // 触发游戏事件（侦测器、Sculk 传感器等）
        rocket.gameEvent(net.minecraft.world.level.gameevent.GameEvent.EXPLODE, rocket.getOwner());

        // 删除实体
        rocket.discard();
    }

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new MathFunctionEnchantmentHandler());
    }

    /**
     * 供 Mixin 查询：该烟花火箭是否处于函数轨迹控制中。
     * Mixin 在 tick() 内部调用，必须是静态方法以避免类加载循环。
     */
    public static boolean isFunctionRocket(FireworkRocketEntity rocket) {
        return rocketDataMap.containsKey(rocket);
    }

    private int getMathFunctionEnchantmentLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var enchantments = stack.getEnchantments();
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            ResourceLocation key = holder.unwrapKey().map(k -> k.location()).orElse(null);
            if (key != null && key.toString().equals("function_math:math_function")) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private boolean hasMathFunctionEnchantment(ItemStack stack) {
        return getMathFunctionEnchantmentLevel(stack) > 0;
    }

    private int getMathDerivativeEnchantmentLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var enchantments = stack.getEnchantments();
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            ResourceLocation key = holder.unwrapKey().map(k -> k.location()).orElse(null);
            if (key != null && key.toString().equals("function_math:math_derivative")) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private boolean hasMathDerivativeEnchantment(ItemStack stack) {
        return getMathDerivativeEnchantmentLevel(stack) > 0;
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // ---------- 处理箭矢 ----------
        if (event.getEntity() instanceof Projectile projectile) {
            LivingEntity shooter = projectile.getOwner() instanceof LivingEntity ? (LivingEntity) projectile.getOwner() : null;
            if (shooter != null) {
                ItemStack mainHand = shooter.getMainHandItem();
                ItemStack offHand = shooter.getOffhandItem();
                int functionLevel = Math.max(getMathFunctionEnchantmentLevel(mainHand), getMathFunctionEnchantmentLevel(offHand));
                int derivativeLevel = Math.max(getMathDerivativeEnchantmentLevel(mainHand), getMathDerivativeEnchantmentLevel(offHand));
                boolean hasFunctionEnchant = functionLevel > 0;
                boolean hasDerivativeEnchant = !hasFunctionEnchant && derivativeLevel > 0;
                if (!hasFunctionEnchant && !hasDerivativeEnchant) return;
                int enchantLevel = hasFunctionEnchant ? functionLevel : derivativeLevel;

                String projectileName = "";
                if (projectile instanceof AbstractArrow arrow && getPickupItemMethod != null) {
                    try {
                        ItemStack pickupItem = (ItemStack) getPickupItemMethod.invoke(arrow);
                        if (pickupItem != null && !pickupItem.isEmpty()) {
                            projectileName = pickupItem.getHoverName().getString();
                        }
                    } catch (Exception e) {
                        com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("获取箭物品失败", e);
                    }
                }
                if (projectileName.isEmpty()) {
                    projectileName = mainHand.getHoverName().getString();
                }

                ExpressionData exprData = parseExpressionData(projectileName);
                String expression = exprData.isDual ? ("y=" + exprData.yExpression + ";z=" + exprData.zExpression) : exprData.yExpression;
                
                if (exprData.isDual && enchantLevel < 2) {
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("1级附魔不支持三维表达式: " + expression);
                    return;
                }
                
                if (!expression.isEmpty() && isExpressionValid(exprData)) {
                    float yaw = shooter.getYRot();
                    double rad = Math.toRadians(yaw);
                    Vec3 baseDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();
                    Vec3 horizontalDir = baseDir;

                    boolean isMultiShot = mainHand.getEnchantmentLevel(shooter.level().holderOrThrow(Enchantments.MULTISHOT)) > 0;
                    if (isMultiShot && projectile instanceof AbstractArrow) {
                        int offsetIndex = Math.abs(projectile.hashCode() % 3);
                        double yawOffset = 0.0;
                        if (offsetIndex == 0) yawOffset = -MULTISHOT_OFFSET;
                        else if (offsetIndex == 2) yawOffset = MULTISHOT_OFFSET;
                        if (Math.abs(yawOffset) > 1e-6) {
                            double cos = Math.cos(yawOffset);
                            double sin = Math.sin(yawOffset);
                            double newX = baseDir.x * cos - baseDir.z * sin;
                            double newZ = baseDir.x * sin + baseDir.z * cos;
                            horizontalDir = new Vec3(newX, 0, newZ).normalize();
                        }
                    }

                    projectile.setDeltaMovement(horizontalDir.scale(HORIZONTAL_SPEED));
                    if (projectile instanceof AbstractArrow arrow) {
                        arrow.setNoGravity(true);
                    }
                    projectile.hasImpulse = true;

                    ProjectileData data = new ProjectileData(
                        expression,
                        exprData.yExpression,
                        exprData.zExpression,
                        exprData.isDual,
                        hasDerivativeEnchant,
                        projectile.position(),
                        horizontalDir,
                        0.0,
                        0,
                        projectile.position()
                    );
                    projectileDataMap.put(projectile, data);
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.info("启用函数轨迹: " + expression + ", 方向: " + horizontalDir);
                }
            }
        }

        // ---------- 处理 TNT（打火石点燃） ----------
        if (event.getEntity() instanceof PrimedTnt tnt) {
            LivingEntity igniter = tnt.getOwner() instanceof LivingEntity ? (LivingEntity) tnt.getOwner() : null;
            if (igniter != null) {
                ItemStack mainHand = igniter.getMainHandItem();
                ItemStack offHand = igniter.getOffhandItem();
                int functionLevel = (mainHand.getItem() == Items.FLINT_AND_STEEL ? getMathFunctionEnchantmentLevel(mainHand) : 0);
                functionLevel = Math.max(functionLevel, (offHand.getItem() == Items.FLINT_AND_STEEL ? getMathFunctionEnchantmentLevel(offHand) : 0));
                int derivativeLevel = (mainHand.getItem() == Items.FLINT_AND_STEEL ? getMathDerivativeEnchantmentLevel(mainHand) : 0);
                derivativeLevel = Math.max(derivativeLevel, (offHand.getItem() == Items.FLINT_AND_STEEL ? getMathDerivativeEnchantmentLevel(offHand) : 0));
                boolean hasFunctionEnchant = functionLevel > 0;
                boolean hasDerivativeEnchant = !hasFunctionEnchant && derivativeLevel > 0;
                if (!hasFunctionEnchant && !hasDerivativeEnchant) return;
                int enchantLevel = hasFunctionEnchant ? functionLevel : derivativeLevel;

                ItemStack flintAndSteel = mainHand.getItem() == Items.FLINT_AND_STEEL ? mainHand : offHand;
                String displayName = flintAndSteel.getHoverName().getString();
                ExpressionData exprData = parseExpressionData(displayName);
                String expression = exprData.isDual ? ("y=" + exprData.yExpression + ";z=" + exprData.zExpression) : exprData.yExpression;
                
                if (exprData.isDual && enchantLevel < 2) {
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("1级附魔不支持三维表达式: " + expression);
                    return;
                }
                
                if (expression.isEmpty() || !isExpressionValid(exprData)) {
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("打火石表达式无效: " + displayName);
                    return;
                }

                tnt.setFuse(200);
                tnt.setNoGravity(true);
                tnt.setDeltaMovement(Vec3.ZERO);
                float yaw = igniter.getYRot();
                double rad = Math.toRadians(yaw);
                Vec3 horizontalDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();

                TNTData data = new TNTData(
                    expression,
                    exprData.yExpression,
                    exprData.zExpression,
                    exprData.isDual,
                    hasDerivativeEnchant,
                    tnt.position().add(new Vec3(0, 0.1, 0)),
                    horizontalDir,
                    0.0,
                    0,
                    tnt.position()
                );
                tntDataMap.put(tnt, data);
                com.ailinmc.function_math.FunctionMathMod.LOGGER.info("启用 TNT 函数轨迹: " + expression);
            }
        }

        // ---------- 处理烟花火箭 ----------
        if (event.getEntity() instanceof FireworkRocketEntity rocket) {
            LivingEntity shooter = rocket.getOwner() instanceof LivingEntity ? (LivingEntity) rocket.getOwner() : null;
            if (shooter == null) return;
            ItemStack mainHand = shooter.getMainHandItem();
            ItemStack offHand = shooter.getOffhandItem();
            int functionLevel = Math.max(getMathFunctionEnchantmentLevel(mainHand), getMathFunctionEnchantmentLevel(offHand));
            int derivativeLevel = Math.max(getMathDerivativeEnchantmentLevel(mainHand), getMathDerivativeEnchantmentLevel(offHand));
            boolean hasFunctionEnchant = functionLevel > 0;
            boolean hasDerivativeEnchant = !hasFunctionEnchant && derivativeLevel > 0;
            if (!hasFunctionEnchant && !hasDerivativeEnchant) return;
            int enchantLevel = hasFunctionEnchant ? functionLevel : derivativeLevel;

            ExpressionData exprData = parseExpressionData(mainHand.getHoverName().getString());
            String expression = exprData.isDual ? ("y=" + exprData.yExpression + ";z=" + exprData.zExpression) : exprData.yExpression;
            
            if (exprData.isDual && enchantLevel < 2) {
                exprData = parseExpressionData(offHand.getHoverName().getString());
                expression = exprData.isDual ? ("y=" + exprData.yExpression + ";z=" + exprData.zExpression) : exprData.yExpression;
            }
            
            if (exprData.isDual && enchantLevel < 2) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("1级附魔不支持三维表达式: " + expression);
                return;
            }
            
            if (expression.isEmpty() || !isExpressionValid(exprData)) {
                exprData = parseExpressionData(offHand.getHoverName().getString());
                expression = exprData.isDual ? ("y=" + exprData.yExpression + ";z=" + exprData.zExpression) : exprData.yExpression;
                
                if (exprData.isDual && enchantLevel < 2) {
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("1级附魔不支持三维表达式: " + expression);
                    return;
                }
                
                if (expression.isEmpty() || !isExpressionValid(exprData)) return;
            }

            rocket.setNoGravity(true);
            rocket.setDeltaMovement(Vec3.ZERO);
            rocket.hasImpulse = false;

            float yaw = shooter.getYRot();
            double rad = Math.toRadians(yaw);
            Vec3 horizontalDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();

            RocketData data = new RocketData(
                expression,
                exprData.yExpression,
                exprData.zExpression,
                exprData.isDual,
                hasDerivativeEnchant,
                rocket.position(),
                horizontalDir,
                0.0,
                0,
                rocket.position()
            );
            rocketDataMap.put(rocket, data);
            com.ailinmc.function_math.FunctionMathMod.LOGGER.info("启用烟花火箭函数轨迹: " + expression);
        }
    }

    private boolean isExpressionValid(String expression) {
        try {
            ExpressionEvaluator.parse(expression);
            return true;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("表达式解析错误: " + expression, e);
            return false;
        }
    }

    private boolean isExpressionValid(ExpressionData exprData) {
        try {
            ExpressionEvaluator.parse(exprData.yExpression);
            if (exprData.isDual) {
                ExpressionEvaluator.parse(exprData.zExpression);
            }
            return true;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("表达式解析错误: " + exprData.yExpression + (exprData.isDual ? (";" + exprData.zExpression) : ""), e);
            return false;
        }
    }

    /** 在 0~maxRange 中寻找第一个使表达式产生有效值的 x */
    private double findValidStartX(String expression, double maxRange) {
        for (double x = 0; x <= maxRange; x += 0.5) {
            try {
                double val = ExpressionEvaluator.evaluate(expression, x);
                if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                    return x;
                }
            } catch (Exception e) {
                // 表达式求值异常，跳过这个 x 值
            }
        }
        return -1; // 找不到有效值
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        projectileDataMap.remove(event.getProjectile());
    }

    @SubscribeEvent
    public void onLivingTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        // ---------- 处理箭矢 ----------
        Iterator<Map.Entry<Projectile, ProjectileData>> projectileIterator = projectileDataMap.entrySet().iterator();
        while (projectileIterator.hasNext()) {
            Map.Entry<Projectile, ProjectileData> entry = projectileIterator.next();
            Projectile projectile = entry.getKey();
            ProjectileData data = entry.getValue();

            if (!projectile.isAlive()) {
                projectileIterator.remove();
                continue;
            }

            if (data.ticks++ > MAX_TICKS || data.projectedDistance > MAX_DISTANCE) {
                revertToGravity(projectile);
                projectileIterator.remove();
                continue;
            }

            if (updateProjectileMovement(projectile, data)) {
                revertToGravity(projectile);
                projectileIterator.remove();
                continue;
            }

            spawnParticleTrail(projectile);
        }

        // ---------- 处理 TNT ----------
        Iterator<Map.Entry<PrimedTnt, TNTData>> tntIterator = tntDataMap.entrySet().iterator();
        while (tntIterator.hasNext()) {
            Map.Entry<PrimedTnt, TNTData> entry = tntIterator.next();
            PrimedTnt tnt = entry.getKey();
            TNTData data = entry.getValue();

            if (!tnt.isAlive()) {
                tntIterator.remove();
                continue;
            }

            if (data.ticks++ > TNT_MAX_TICKS || data.projectedDistance > TNT_MAX_DISTANCE) {
                explodeTnt(tnt);
                tntIterator.remove();
                continue;
            }

            if (updateTNTMovement(tnt, data)) {
                explodeTnt(tnt);
                tntIterator.remove();
                continue;
            }

            if (checkCollisionAndExplode(tnt)) {
                tntIterator.remove();
                continue;
            }

            spawnTntParticles(tnt);
        }

        // ---------- 处理烟花火箭 ----------
        Iterator<Map.Entry<FireworkRocketEntity, RocketData>> rocketIterator = rocketDataMap.entrySet().iterator();
        while (rocketIterator.hasNext()) {
            Map.Entry<FireworkRocketEntity, RocketData> entry = rocketIterator.next();
            FireworkRocketEntity rocket = entry.getKey();
            RocketData data = entry.getValue();

            if (!rocket.isAlive()) {
                rocketIterator.remove();
                continue;
            }

            if (data.ticks++ > MAX_TICKS || data.projectedDistance > MAX_DISTANCE) {
                rocket.discard();
                rocketIterator.remove();
                continue;
            }

            if (updateRocketMovement(rocket, data)) {
                // 碰撞爆炸时 explodeRocket() 内部已经 discard()，此处 isAlive() 为 false，
                // 重复调用 discard() 无副作用；超时/表达式错误等情况则由此处负责清理。
                if (rocket.isAlive()) {
                    rocket.discard();
                }
                rocketIterator.remove();
                continue;
            }

            spawnRocketParticles(rocket);
        }
    }

    // ---------- 辅助方法 ----------
    private void revertToGravity(Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoGravity(false);
        }
        com.ailinmc.function_math.FunctionMathMod.LOGGER.info("函数轨迹结束，恢复重力: " + projectile);
    }

    private double derivative(String expression, double x) {
        try {
            double fx = ExpressionEvaluator.evaluate(expression, x);
            double fxDelta = ExpressionEvaluator.evaluate(expression, x + DELTA_X_FOR_DERIVATIVE);
            return (fxDelta - fx) / DELTA_X_FOR_DERIVATIVE;
        } catch (Exception e) {
            return 0;
        }
    }

    private double secondDerivative(String expression, double x) {
        try {
            double fx = ExpressionEvaluator.evaluate(expression, x);
            double fxDelta = ExpressionEvaluator.evaluate(expression, x + DELTA_X_FOR_DERIVATIVE);
            double fx2Delta = ExpressionEvaluator.evaluate(expression, x + 2 * DELTA_X_FOR_DERIVATIVE);
            double dx = DELTA_X_FOR_DERIVATIVE;
            return (fx2Delta - 2 * fxDelta + fx) / (dx * dx);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean updateProjectileMovement(Projectile projectile, ProjectileData data) {
        projectile.setNoGravity(true);
        Vec3 rightDir = new Vec3(-data.lookVector.z, 0, data.lookVector.x).normalize();

        if (data.firstTick) {
            data.firstTick = false;
            double startX = findValidStartX(data.yExpression, 10.0);
            if (startX < 0) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("无法找到有效的起始x值: " + data.expression);
                return true;
            }
            data.projectedDistance = startX * TRAJECTORY_SCALE;
            try {
                double f0;
                if (data.isDerivative) {
                    f0 = derivative(data.yExpression, startX);
                } else {
                    f0 = ExpressionEvaluator.evaluate(data.yExpression, startX);
                }
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

                if (data.isDual) {
                    double z0;
                    if (data.isDerivative) {
                        z0 = derivative(data.zExpression, startX);
                    } else {
                        z0 = ExpressionEvaluator.evaluate(data.zExpression, startX);
                    }
                    targetX += rightDir.x * z0;
                    targetZ += rightDir.z * z0;
                }

                Vec3 target = new Vec3(targetX, targetY, targetZ);
                projectile.setPos(target.x, target.y, target.z);
                data.lastPos = target;

                double slope0;
                if (data.isDerivative) {
                    slope0 = secondDerivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                } else {
                    slope0 = derivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                }
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;

                if (data.isDual) {
                    double zSlope0;
                    if (data.isDerivative) {
                        zSlope0 = secondDerivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    } else {
                        zSlope0 = derivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    }
                    tanX0 += rightDir.x * zSlope0 * HORIZONTAL_SPEED;
                    tanZ0 += rightDir.z * zSlope0 * HORIZONTAL_SPEED;
                }

                double horizMag0 = Math.sqrt(tanX0 * tanX0 + tanZ0 * tanZ0);
                if (horizMag0 > 1e-8 || Math.abs(tanY0) > 1e-8) {
                    float yaw0 = (float) (Math.atan2(tanX0, tanZ0) * 180.0 / Math.PI);
                    float pitch0 = (float) (Math.atan2(tanY0, horizMag0) * 180.0 / Math.PI);
                    projectile.setYRot(yaw0);
                    projectile.setXRot(pitch0);
                }
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("计算起始位置失败", e);
                return true;
            }
            return false;
        }

        data.projectedDistance += HORIZONTAL_SPEED;
        double xMath = data.projectedDistance / TRAJECTORY_SCALE;

        try {
            double rawY;
            if (data.isDerivative) {
                rawY = derivative(data.yExpression, xMath);
            } else {
                rawY = ExpressionEvaluator.evaluate(data.yExpression, xMath);
            }
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                return false;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            if (data.isDual) {
                double rawZ;
                if (data.isDerivative) {
                    rawZ = derivative(data.zExpression, xMath);
                } else {
                    rawZ = ExpressionEvaluator.evaluate(data.zExpression, xMath);
                }
                if (Double.isNaN(rawZ) || Double.isInfinite(rawZ)) {
                    return false;
                }
                targetX += rightDir.x * rawZ;
                targetZ += rightDir.z * rawZ;
            }

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);

            double slope;
            if (data.isDerivative) {
                slope = secondDerivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            } else {
                slope = derivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            }
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;

            if (data.isDual) {
                double zSlope;
                if (data.isDerivative) {
                    zSlope = secondDerivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                } else {
                    zSlope = derivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                }
                tanX += rightDir.x * zSlope * HORIZONTAL_SPEED;
                tanZ += rightDir.z * zSlope * HORIZONTAL_SPEED;
            }

            Vec3 tangentVelocity = new Vec3(tanX, tanY, tanZ);

            if (hasSolidCollisionBetween(projectile, data.lastPos, newPos)) {
                handleBlockHit(projectile, tangentVelocity);
                return true;
            }

            Entity hitEntity = getEntityHit(projectile, data.lastPos, newPos);
            if (hitEntity != null) {
                handleEntityHit(projectile, hitEntity, tangentVelocity);
                return true;
            }

            projectile.setPos(targetX, targetY, targetZ);

            double horizMag = Math.sqrt(tanX * tanX + tanZ * tanZ);
            if (horizMag > 1e-8 || Math.abs(tanY) > 1e-8) {
                float yaw = (float) (Math.atan2(tanX, tanZ) * 180.0 / Math.PI);
                float pitch = (float) (Math.atan2(tanY, horizMag) * 180.0 / Math.PI);
                projectile.setYRot(yaw);
                projectile.setXRot(pitch);
            }

            data.lastPos = newPos;
            projectile.hasImpulse = true;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("表达式计算错误", e);
            return true;
        }

        return false;
    }

    // ---------- TNT 运动更新 ----------
    private boolean updateTNTMovement(PrimedTnt tnt, TNTData data) {
        Vec3 rightDir = new Vec3(-data.lookVector.z, 0, data.lookVector.x).normalize();

        if (data.firstTick) {
            data.firstTick = false;
            double startX = findValidStartX(data.yExpression, 10.0);
            if (startX < 0) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("TNT 无法找到有效的起始x值: " + data.expression);
                return true;
            }
            data.projectedDistance = startX * TRAJECTORY_SCALE;
            try {
                double f0;
                if (data.isDerivative) {
                    f0 = derivative(data.yExpression, startX);
                } else {
                    f0 = ExpressionEvaluator.evaluate(data.yExpression, startX);
                }
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

                if (data.isDual) {
                    double z0;
                    if (data.isDerivative) {
                        z0 = derivative(data.zExpression, startX);
                    } else {
                        z0 = ExpressionEvaluator.evaluate(data.zExpression, startX);
                    }
                    targetX += rightDir.x * z0;
                    targetZ += rightDir.z * z0;
                }

                Vec3 target = new Vec3(targetX, targetY, targetZ);
                tnt.setPos(target.x, target.y, target.z);
                data.lastPos = target;

                double slope0;
                if (data.isDerivative) {
                    slope0 = secondDerivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                } else {
                    slope0 = derivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                }
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;

                if (data.isDual) {
                    double zSlope0;
                    if (data.isDerivative) {
                        zSlope0 = secondDerivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    } else {
                        zSlope0 = derivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    }
                    tanX0 += rightDir.x * zSlope0 * HORIZONTAL_SPEED;
                    tanZ0 += rightDir.z * zSlope0 * HORIZONTAL_SPEED;
                }

                double horizMag0 = Math.sqrt(tanX0 * tanX0 + tanZ0 * tanZ0);
                if (horizMag0 > 1e-8 || Math.abs(tanY0) > 1e-8) {
                    float yaw0 = (float) (Math.atan2(tanX0, tanZ0) * 180.0 / Math.PI);
                    float pitch0 = (float) (Math.atan2(tanY0, horizMag0) * 180.0 / Math.PI);
                    tnt.setYRot(yaw0);
                    tnt.setXRot(pitch0);
                }
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("TNT 计算起始位置失败", e);
                return true;
            }
            return false;
        }

        data.projectedDistance += HORIZONTAL_SPEED;
        double xMath = data.projectedDistance / TRAJECTORY_SCALE;

        try {
            double rawY;
            if (data.isDerivative) {
                rawY = derivative(data.yExpression, xMath);
            } else {
                rawY = ExpressionEvaluator.evaluate(data.yExpression, xMath);
            }
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                return false;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            if (data.isDual) {
                double rawZ;
                if (data.isDerivative) {
                    rawZ = derivative(data.zExpression, xMath);
                } else {
                    rawZ = ExpressionEvaluator.evaluate(data.zExpression, xMath);
                }
                if (Double.isNaN(rawZ) || Double.isInfinite(rawZ)) {
                    return false;
                }
                targetX += rightDir.x * rawZ;
                targetZ += rightDir.z * rawZ;
            }

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);
            Vec3 delta = newPos.subtract(data.lastPos);

            if (hasSolidCollisionBetween(tnt, data.lastPos, newPos)) {
                explodeTnt(tnt);
                return true;
            }

            tnt.setPos(targetX, targetY, targetZ);
            tnt.setDeltaMovement(delta);

            double slope;
            if (data.isDerivative) {
                slope = secondDerivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            } else {
                slope = derivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            }
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;

            if (data.isDual) {
                double zSlope;
                if (data.isDerivative) {
                    zSlope = secondDerivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                } else {
                    zSlope = derivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                }
                tanX += rightDir.x * zSlope * HORIZONTAL_SPEED;
                tanZ += rightDir.z * zSlope * HORIZONTAL_SPEED;
            }

            double horizMag = Math.sqrt(tanX * tanX + tanZ * tanZ);
            if (horizMag > 1e-8 || Math.abs(tanY) > 1e-8) {
                float yaw = (float) (Math.atan2(tanX, tanZ) * 180.0 / Math.PI);
                float pitch = (float) (Math.atan2(tanY, horizMag) * 180.0 / Math.PI);
                tnt.setYRot(yaw);
                tnt.setXRot(pitch);
            }

            data.lastPos = newPos;
            tnt.hasImpulse = true;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("TNT 表达式计算错误", e);
            return true;
        }

        return false;
    }

    private boolean hasSolidCollisionBetween(Entity entity, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1e-8) return false;
        Vec3 step = delta.normalize();
        int steps = (int) Math.ceil(length / 0.1);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 pos = from.add(step.scale(t * length));
            AABB aabb = entity.getBoundingBox().move(pos.subtract(entity.position()));
            if (hasSolidCollision(entity, aabb)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSolidCollision(Entity entity, AABB aabb) {
        Level level = entity.level();
        int minX = (int) Math.floor(aabb.minX);
        int maxX = (int) Math.ceil(aabb.maxX);
        int minY = (int) Math.floor(aabb.minY);
        int maxY = (int) Math.ceil(aabb.maxY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxZ = (int) Math.ceil(aabb.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var blockState = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (!blockState.getCollisionShape(level, new net.minecraft.core.BlockPos(x, y, z)).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handleBlockHit(Projectile projectile, Vec3 tangentVelocity) {
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoGravity(false);
        }
        projectile.setDeltaMovement(tangentVelocity);
        projectile.hasImpulse = true;
        com.ailinmc.function_math.FunctionMathMod.LOGGER.info("弹射物碰撞方块，恢复原版逻辑: " + projectile);
    }

    private void handleEntityHit(Projectile projectile, Entity hitEntity, Vec3 tangentVelocity) {
        if (projectile instanceof AbstractArrow arrow && onHitEntityMethod != null) {
            arrow.setNoGravity(false);
            arrow.setDeltaMovement(tangentVelocity);
            arrow.hasImpulse = true;
            try {
                net.minecraft.world.phys.EntityHitResult hitResult = new net.minecraft.world.phys.EntityHitResult(hitEntity);
                onHitEntityMethod.invoke(arrow, hitResult);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("调用 onHitEntity 失败", e);
            }
        } else {
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setNoGravity(false);
            }
            projectile.setDeltaMovement(tangentVelocity);
            projectile.hasImpulse = true;
        }
        com.ailinmc.function_math.FunctionMathMod.LOGGER.info("弹射物碰撞实体: " + projectile + " -> " + hitEntity);
    }

    private Entity getEntityHit(Projectile projectile, Vec3 from, Vec3 to) {
        Level level = projectile.level();
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1e-8) return null;
        Vec3 direction = delta.normalize();
        
        AABB searchBox = new AABB(from.x, from.y, from.z, to.x, to.y, to.z).inflate(0.3);
        for (Entity entity : level.getEntities(projectile, searchBox)) {
            if (entity == projectile || entity == projectile.getOwner()) continue;
            if (lineIntersectsAABB(from, direction, length, entity.getBoundingBox())) {
                return entity;
            }
        }
        return null;
    }

    private boolean lineIntersectsAABB(Vec3 origin, Vec3 direction, double maxDistance, AABB aabb) {
        double tMin = (aabb.minX - origin.x) / direction.x;
        double tMax = (aabb.maxX - origin.x) / direction.x;
        if (tMin > tMax) { double tmp = tMin; tMin = tMax; tMax = tmp; }
        
        double tyMin = (aabb.minY - origin.y) / direction.y;
        double tyMax = (aabb.maxY - origin.y) / direction.y;
        if (tyMin > tyMax) { double tmp = tyMin; tyMin = tyMax; tyMax = tmp; }
        
        if (tMin > tyMax || tyMin > tMax) return false;
        tMin = Math.max(tMin, tyMin);
        tMax = Math.min(tMax, tyMax);
        
        double tzMin = (aabb.minZ - origin.z) / direction.z;
        double tzMax = (aabb.maxZ - origin.z) / direction.z;
        if (tzMin > tzMax) { double tmp = tzMin; tzMin = tzMax; tzMax = tmp; }
        
        if (tMin > tzMax || tzMin > tMax) return false;
        tMin = Math.max(tMin, tzMin);
        tMax = Math.min(tMax, tzMax);
        
        return tMin >= 0 && tMin <= maxDistance;
    }

    private boolean checkCollisionAndExplode(PrimedTnt tnt) {
        Level level = tnt.level();
        AABB boundingBox = tnt.getBoundingBox();
        Vec3 motion = tnt.getDeltaMovement();
        if (motion.lengthSqr() > 0.0001) {
            AABB newBox = boundingBox.move(motion);
            if (hasSolidCollision(tnt, newBox)) {
                explodeTnt(tnt);
                return true;
            }
        }
        for (Entity entity : level.getEntities(tnt, boundingBox.inflate(0.2))) {
            if (entity != tnt && entity != tnt.getOwner()) {
                explodeTnt(tnt);
                return true;
            }
        }
        return false;
    }

    private void explodeTnt(PrimedTnt tnt) {
        if (!tnt.isRemoved()) {
            Level level = tnt.level();
            level.explode(tnt, tnt.getX(), tnt.getY(), tnt.getZ(), 4.0F, Level.ExplosionInteraction.TNT);
            tnt.discard();
            com.ailinmc.function_math.FunctionMathMod.LOGGER.info("TNT 函数轨迹爆炸");
        }
    }

    // ---------- 烟花火箭运动更新 ----------
    private boolean updateRocketMovement(FireworkRocketEntity rocket, RocketData data) {
        rocket.setDeltaMovement(Vec3.ZERO);
        rocket.setNoGravity(true);
        Vec3 rightDir = new Vec3(-data.lookVector.z, 0, data.lookVector.x).normalize();

        if (data.firstTick) {
            data.firstTick = false;
            double startX = findValidStartX(data.yExpression, 10.0);
            if (startX < 0) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("火箭无法找到有效的起始x值: " + data.expression);
                return true;
            }
            data.projectedDistance = startX * TRAJECTORY_SCALE;
            try {
                double f0;
                if (data.isDerivative) {
                    f0 = derivative(data.yExpression, startX);
                } else {
                    f0 = ExpressionEvaluator.evaluate(data.yExpression, startX);
                }
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

                if (data.isDual) {
                    double z0;
                    if (data.isDerivative) {
                        z0 = derivative(data.zExpression, startX);
                    } else {
                        z0 = ExpressionEvaluator.evaluate(data.zExpression, startX);
                    }
                    targetX += rightDir.x * z0;
                    targetZ += rightDir.z * z0;
                }

                Vec3 target = new Vec3(targetX, targetY, targetZ);
                rocket.setPos(target.x, target.y, target.z);
                data.lastPos = target;

                double slope0;
                if (data.isDerivative) {
                    slope0 = secondDerivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                } else {
                    slope0 = derivative(data.yExpression, startX) / TRAJECTORY_SCALE;
                }
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;

                if (data.isDual) {
                    double zSlope0;
                    if (data.isDerivative) {
                        zSlope0 = secondDerivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    } else {
                        zSlope0 = derivative(data.zExpression, startX) / TRAJECTORY_SCALE;
                    }
                    tanX0 += rightDir.x * zSlope0 * HORIZONTAL_SPEED;
                    tanZ0 += rightDir.z * zSlope0 * HORIZONTAL_SPEED;
                }

                double horizMag0 = Math.sqrt(tanX0 * tanX0 + tanZ0 * tanZ0);
                if (horizMag0 > 1e-8 || Math.abs(tanY0) > 1e-8) {
                    float yaw0 = (float) (Math.atan2(tanX0, tanZ0) * 180.0 / Math.PI);
                    float pitch0 = (float) (Math.atan2(tanY0, horizMag0) * 180.0 / Math.PI);
                    rocket.setYRot(yaw0);
                    rocket.setXRot(pitch0);
                }
                rocket.setDeltaMovement(Vec3.ZERO);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("火箭计算起始位置失败", e);
                return true;
            }
            return false;
        }

        data.projectedDistance += HORIZONTAL_SPEED;
        double xMath = data.projectedDistance / TRAJECTORY_SCALE;

        try {
            double rawY;
            if (data.isDerivative) {
                rawY = derivative(data.yExpression, xMath);
            } else {
                rawY = ExpressionEvaluator.evaluate(data.yExpression, xMath);
            }
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                return false;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            if (data.isDual) {
                double rawZ;
                if (data.isDerivative) {
                    rawZ = derivative(data.zExpression, xMath);
                } else {
                    rawZ = ExpressionEvaluator.evaluate(data.zExpression, xMath);
                }
                if (Double.isNaN(rawZ) || Double.isInfinite(rawZ)) {
                    return false;
                }
                targetX += rightDir.x * rawZ;
                targetZ += rightDir.z * rawZ;
            }

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);

            double slope;
            if (data.isDerivative) {
                slope = secondDerivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            } else {
                slope = derivative(data.yExpression, xMath) / TRAJECTORY_SCALE;
            }
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;

            if (data.isDual) {
                double zSlope;
                if (data.isDerivative) {
                    zSlope = secondDerivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                } else {
                    zSlope = derivative(data.zExpression, xMath) / TRAJECTORY_SCALE;
                }
                tanX += rightDir.x * zSlope * HORIZONTAL_SPEED;
                tanZ += rightDir.z * zSlope * HORIZONTAL_SPEED;
            }

            Vec3 tangentVelocity = new Vec3(tanX, tanY, tanZ);

            if (hasSolidCollisionBetween(rocket, data.lastPos, newPos)) {
                explodeRocket(rocket, newPos);
                return true;
            }

            Entity hitEntity = getEntityHit(rocket, data.lastPos, newPos);
            if (hitEntity != null) {
                explodeRocket(rocket, hitEntity.position());
                return true;
            }

            rocket.setPos(targetX, targetY, targetZ);
            rocket.setDeltaMovement(Vec3.ZERO);

            double horizMag = Math.sqrt(tanX * tanX + tanZ * tanZ);
            if (horizMag > 1e-8 || Math.abs(tanY) > 1e-8) {
                float yaw = (float) (Math.atan2(tanX, tanZ) * 180.0 / Math.PI);
                float pitch = (float) (Math.atan2(tanY, horizMag) * 180.0 / Math.PI);
                rocket.setYRot(yaw);
                rocket.setXRot(pitch);
            }

            data.lastPos = newPos;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("火箭表达式计算错误", e);
            return true;
        }

        return false;
    }

    // ---------- 粒子效果 ----------
    private void spawnParticleTrail(Projectile projectile) {
        Level level = projectile.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 pos = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        double speed = motion.length();
        if (speed < 0.05) return;

        Vec3 back = motion.normalize().scale(-0.5);
        Vec3 particlePos = pos.add(back);

        int particleCount = (projectile instanceof AbstractArrow) ? 9 : 6;
        double particleMovement = (projectile instanceof AbstractArrow) ? 0.15 : 0.1;

        for (int i = 0; i < particleCount; i++) {
            serverLevel.sendParticles(
                ParticleTypes.FIREWORK,
                particlePos.x(), particlePos.y(), particlePos.z(),
                1,
                (float) particleMovement, (float) particleMovement, (float) particleMovement,
                0.0
            );
        }
    }

    private void spawnTntParticles(PrimedTnt tnt) {
        Level level = tnt.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 pos = tnt.position();
        Vec3 motion = tnt.getDeltaMovement();
        if (motion.lengthSqr() < 0.01) return;
        Vec3 back = motion.normalize().scale(-0.3);
        Vec3 particlePos = pos.add(back);
        serverLevel.sendParticles(
            ParticleTypes.FLAME,
            particlePos.x(), particlePos.y(), particlePos.z(),
            6,
            0.15, 0.15, 0.15,
            0.02
        );
    }

    private void spawnRocketParticles(FireworkRocketEntity rocket) {
        Level level = rocket.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 pos = rocket.position();
        // 烟花火箭自带粒子，这里额外加一些彩色粒子或火花，可根据喜好调整
        serverLevel.sendParticles(
            ParticleTypes.FIREWORK,
            pos.x(), pos.y(), pos.z(),
            3,
            0.1, 0.1, 0.1,
            0.01
        );
    }

    private static class ExpressionData {
        public final String yExpression;
        public final String zExpression;
        public final boolean isDual;

        public ExpressionData(String yExpression, String zExpression) {
            this.yExpression = yExpression;
            this.zExpression = zExpression;
            this.isDual = zExpression != null && !zExpression.isEmpty();
        }

        public static ExpressionData parse(String displayName) {
            Matcher dualMatcher = DUAL_EXPRESSION_PATTERN.matcher(displayName);
            if (dualMatcher.matches()) {
                return new ExpressionData(dualMatcher.group(1).trim(), dualMatcher.group(2).trim());
            }
            Matcher zOnlyMatcher = Z_ONLY_PATTERN.matcher(displayName);
            if (zOnlyMatcher.matches()) {
                return new ExpressionData("0", zOnlyMatcher.group(1).trim());
            }
            Matcher singleMatcher = EXPRESSION_PATTERN.matcher(displayName);
            if (singleMatcher.matches()) {
                return new ExpressionData(singleMatcher.group(2).trim(), null);
            }
            return new ExpressionData("", null);
        }
    }

    private String extractExpression(String displayName) {
        ExpressionData data = ExpressionData.parse(displayName);
        if (data.isDual) {
            return "y=" + data.yExpression + ";z=" + data.zExpression;
        }
        return data.yExpression;
    }

    private ExpressionData parseExpressionData(String displayName) {
        return ExpressionData.parse(displayName);
    }

    // ---------- 数据类 ----------
    private static class ProjectileData {
        public final String expression;
        public final String yExpression;
        public final String zExpression;
        public final boolean isDual;
        public final boolean isDerivative;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;   // 用于计算朝向

        public ProjectileData(String expression, String yExpression, String zExpression, boolean isDual, boolean isDerivative,
                              Vec3 origin, Vec3 lookVector, double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
            this.yExpression = yExpression;
            this.zExpression = zExpression;
            this.isDual = isDual;
            this.isDerivative = isDerivative;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
            this.lastPos = lastPos;
        }
    }

    private static class TNTData {
        public final String expression;
        public final String yExpression;
        public final String zExpression;
        public final boolean isDual;
        public final boolean isDerivative;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;

        public TNTData(String expression, String yExpression, String zExpression, boolean isDual, boolean isDerivative,
                       Vec3 origin, Vec3 lookVector, double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
            this.yExpression = yExpression;
            this.zExpression = zExpression;
            this.isDual = isDual;
            this.isDerivative = isDerivative;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
            this.lastPos = lastPos;
        }
    }

    private static class RocketData {
        public final String expression;
        public final String yExpression;
        public final String zExpression;
        public final boolean isDual;
        public final boolean isDerivative;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;

        public RocketData(String expression, String yExpression, String zExpression, boolean isDual, boolean isDerivative,
                          Vec3 origin, Vec3 lookVector, double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
            this.yExpression = yExpression;
            this.zExpression = zExpression;
            this.isDual = isDual;
            this.isDerivative = isDerivative;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
            this.lastPos = lastPos;
        }
    }
}