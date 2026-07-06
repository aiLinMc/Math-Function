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
    }

    /**
     * 将烟花火箭移动到指定位置后立即引爆，完整复现原版 explode() 的逻辑：
     *   1. broadcastEntityEvent(17)  — 触发客户端爆炸粒子 + 声音
     *   2. dealExplosionDamage()     — 计算并施加范围伤害（反射调用）
     *   3. gameEvent(EXPLODE)        — 触发侦测器 / Sculk 传感器
     *   4. discard()                 — 删除实体
     */
    private void explodeRocket(FireworkRocketEntity rocket, Vec3 explosionPos) {
        if (rocket.isRemoved()) return;

        // 移到碰撞点，确保爆炸粒子和伤害范围的位置正确
        rocket.setPos(explosionPos.x, explosionPos.y, explosionPos.z);

        // 触发客户端爆炸粒子 + 声音（byte 17 = 烟花爆炸事件，见 handleEntityEvent）
        rocket.level().broadcastEntityEvent(rocket, (byte) 17);

        // 施加范围伤害（原版 dealExplosionDamage：
        //   根据 Fireworks 组件的爆炸列表计算基础伤害 5 + 爆炸数*2，
        //   对半径 5 格内有视线的 LivingEntity 造成按距离衰减的伤害）
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

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // ---------- 处理箭矢 ----------
        if (event.getEntity() instanceof Projectile projectile) {
            LivingEntity shooter = projectile.getOwner() instanceof LivingEntity ? (LivingEntity) projectile.getOwner() : null;
            if (shooter != null) {
                ItemStack mainHand = shooter.getMainHandItem();
                ItemStack offHand = shooter.getOffhandItem();
                boolean hasEnchant = hasMathFunctionEnchantment(mainHand) || hasMathFunctionEnchantment(offHand);
                if (!hasEnchant) return;

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

                String expression = extractExpression(projectileName);
                if (!expression.isEmpty() && isExpressionValid(expression)) {
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
                        projectile.position(),   // 以实体实际位置为原点
                        horizontalDir,
                        0.0,
                        0,
                        projectile.position()    // lastPos 初始为当前位置
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
                boolean hasEnchant = (mainHand.getItem() == Items.FLINT_AND_STEEL && hasMathFunctionEnchantment(mainHand)) ||
                                     (offHand.getItem() == Items.FLINT_AND_STEEL && hasMathFunctionEnchantment(offHand));
                if (!hasEnchant) return;

                ItemStack flintAndSteel = mainHand.getItem() == Items.FLINT_AND_STEEL ? mainHand : offHand;
                String displayName = flintAndSteel.getHoverName().getString();
                String expression = extractExpression(displayName);
                if (expression.isEmpty() || !isExpressionValid(expression)) {
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("打火石表达式无效: " + displayName);
                    return;
                }

                tnt.setFuse(200);
                tnt.setNoGravity(true);
                tnt.setDeltaMovement(Vec3.ZERO);
                float yaw = igniter.getYRot();
                double rad = Math.toRadians(yaw);
                Vec3 horizontalDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();

                // 关键修改：origin 使用 tnt 的实际生成位置
                TNTData data = new TNTData(
                    expression,
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
            boolean hasEnchant = hasMathFunctionEnchantment(mainHand) || hasMathFunctionEnchantment(offHand);
            if (!hasEnchant) return;

            // 获取表达式（优先从主手物品名称提取，也可根据实际情况调整）
            String displayName = mainHand.getHoverName().getString();
            String expression = extractExpression(displayName);
            if (expression.isEmpty() || !isExpressionValid(expression)) {
                // 若主手没有，试试副手
                displayName = offHand.getHoverName().getString();
                expression = extractExpression(displayName);
                if (expression.isEmpty() || !isExpressionValid(expression)) return;
            }

            // 禁用重力和初速度，并持续压制原版烟花推进逻辑
            rocket.setNoGravity(true);
            rocket.setDeltaMovement(Vec3.ZERO);
            rocket.hasImpulse = false;

            // 计算水平方向（同箭矢）
            float yaw = shooter.getYRot();
            double rad = Math.toRadians(yaw);
            Vec3 horizontalDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();

            // 以火箭当前位置为原点
            RocketData data = new RocketData(
                expression,
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
            double testY0 = ExpressionEvaluator.evaluate(expression, 0);
            double testY1 = ExpressionEvaluator.evaluate(expression, 1);
            if (Double.isNaN(testY0) || Double.isInfinite(testY0)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("表达式在x=0处产生无效值: " + expression);
                return false;
            }
            if (Double.isNaN(testY1) || Double.isInfinite(testY1)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("表达式在x=1处产生无效值: " + expression);
                return false;
            }
            return true;
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("表达式解析错误: " + expression, e);
            return false;
        }
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
        double fx = ExpressionEvaluator.evaluate(expression, x);
        double fxDelta = ExpressionEvaluator.evaluate(expression, x + DELTA_X_FOR_DERIVATIVE);
        return (fxDelta - fx) / DELTA_X_FOR_DERIVATIVE;
    }

    private boolean updateProjectileMovement(Projectile projectile, ProjectileData data) {
        if (data.firstTick) {
            data.firstTick = false;
            data.projectedDistance = 0.0;
            try {
                double f0 = ExpressionEvaluator.evaluate(data.expression, 0.0);
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;
                Vec3 target = new Vec3(targetX, targetY, targetZ);
                projectile.setPos(target.x, target.y, target.z);
                data.lastPos = target;
                double slope0 = derivative(data.expression, 0.0) / TRAJECTORY_SCALE;
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;
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
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("表达式在x=" + xMath + "处无效");
                return true;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);

            double slope = derivative(data.expression, xMath) / TRAJECTORY_SCALE;
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;
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
        if (data.firstTick) {
            data.firstTick = false;
            data.projectedDistance = 0.0;
            try {
                double f0 = ExpressionEvaluator.evaluate(data.expression, 0.0);
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;
                Vec3 target = new Vec3(targetX, targetY, targetZ);
                tnt.setPos(target.x, target.y, target.z);
                data.lastPos = target;
                // 用 x=0 处的切线斜率初始化朝向
                double slope0 = derivative(data.expression, 0.0) / TRAJECTORY_SCALE;
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;
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
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("TNT 表达式在x=" + xMath + "处无效");
                return true;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);
            Vec3 delta = newPos.subtract(data.lastPos);

            // 碰撞预检测（若路径上有方块则立即爆炸）
            if (hasSolidCollisionBetween(tnt, data.lastPos, newPos)) {
                explodeTnt(tnt);
                return true;
            }

            tnt.setPos(targetX, targetY, targetZ);
            tnt.setDeltaMovement(delta);  // 设置运动用于碰撞检测，但已禁用重力

            // 用解析切线向量计算朝向（pitch 正值=俯视，故上升段用 +tanY）
            double slope = derivative(data.expression, xMath) / TRAJECTORY_SCALE;
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;
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
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setNoGravity(false);
        }
        projectile.setDeltaMovement(tangentVelocity);
        projectile.hasImpulse = true;
        com.ailinmc.function_math.FunctionMathMod.LOGGER.info("弹射物碰撞实体，恢复原版逻辑: " + projectile + " -> " + hitEntity);
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
        // 每 tick 首先强制清零速度，压制原版烟花推进逻辑（原版在自己的 tick() 里会累加向上速度）
        // 必须在位置计算前清零，否则原版物理会在我们 setPos 之前先移动实体，造成抖动
        rocket.setDeltaMovement(Vec3.ZERO);
        rocket.setNoGravity(true);

        if (data.firstTick) {
            data.firstTick = false;
            data.projectedDistance = 0.0;
            try {
                double f0 = ExpressionEvaluator.evaluate(data.expression, 0.0);
                double targetY = data.origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
                double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;
                Vec3 target = new Vec3(targetX, targetY, targetZ);
                rocket.setPos(target.x, target.y, target.z);
                data.lastPos = target;
                // 用 x=0 处的切线斜率初始化朝向
                double slope0 = derivative(data.expression, 0.0) / TRAJECTORY_SCALE;
                double tanX0 = data.lookVector.x * HORIZONTAL_SPEED;
                double tanY0 = slope0 * HORIZONTAL_SPEED;
                double tanZ0 = data.lookVector.z * HORIZONTAL_SPEED;
                double horizMag0 = Math.sqrt(tanX0 * tanX0 + tanZ0 * tanZ0);
                if (horizMag0 > 1e-8 || Math.abs(tanY0) > 1e-8) {
                    float yaw0 = (float) (Math.atan2(tanX0, tanZ0) * 180.0 / Math.PI);
                    float pitch0 = (float) (Math.atan2(tanY0, horizMag0) * 180.0 / Math.PI);
                    rocket.setYRot(yaw0);
                    rocket.setXRot(pitch0);
                }
                // setPos 之后再次清零，防止本帧剩余逻辑还有速度残留
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
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("火箭表达式在x=" + xMath + "处无效");
                return true;
            }

            double targetY = data.origin.y + rawY;
            targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
            double targetX = data.origin.x + data.lookVector.x * data.projectedDistance;
            double targetZ = data.origin.z + data.lookVector.z * data.projectedDistance;

            Vec3 newPos = new Vec3(targetX, targetY, targetZ);

            double slope = derivative(data.expression, xMath) / TRAJECTORY_SCALE;
            double tanX = data.lookVector.x * HORIZONTAL_SPEED;
            double tanY = slope * HORIZONTAL_SPEED;
            double tanZ = data.lookVector.z * HORIZONTAL_SPEED;
            Vec3 tangentVelocity = new Vec3(tanX, tanY, tanZ);

            if (hasSolidCollisionBetween(rocket, data.lastPos, newPos)) {
                // 碰到方块：在碰撞点爆炸，与原版 onHitBlock 行为一致
                explodeRocket(rocket, newPos);
                return true;
            }

            Entity hitEntity = getEntityHit(rocket, data.lastPos, newPos);
            if (hitEntity != null) {
                // 碰到实体：在实体位置爆炸，与原版 onHitEntity 行为一致
                explodeRocket(rocket, hitEntity.position());
                return true;
            }

            rocket.setPos(targetX, targetY, targetZ);
            // setPos 之后立即再次清零速度：原版 FireworkRocketEntity.tick() 在同一帧内可能还有后续逻辑，
            // 如果此时速度不为零，下一帧开始时实体会被原版逻辑再移动一次，产生"飞起来又被拉回"的抖动。
            rocket.setDeltaMovement(Vec3.ZERO);

            double horizMag = Math.sqrt(tanX * tanX + tanZ * tanZ);
            if (horizMag > 1e-8 || Math.abs(tanY) > 1e-8) {
                float yaw = (float) (Math.atan2(tanX, tanZ) * 180.0 / Math.PI);
                float pitch = (float) (Math.atan2(tanY, horizMag) * 180.0 / Math.PI);
                rocket.setYRot(yaw);
                rocket.setXRot(pitch);
            }

            data.lastPos = newPos;
            // 不设置 hasImpulse = true：hasImpulse 会触发原版网络同步中的速度广播，
            // 导致客户端根据速度向量再做一次位移预测，与我们直接 setPos 的结果冲突，加剧抖动。
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

    // ---------- 表达式提取 ----------
    private String extractExpression(String displayName) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(displayName);
        if (matcher.matches()) {
            return matcher.group(2).trim();
        }
        return "";
    }

    // ---------- 数据类 ----------
    private static class ProjectileData {
        public final String expression;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;   // 用于计算朝向

        public ProjectileData(String expression, Vec3 origin, Vec3 lookVector,
                              double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
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
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;

        public TNTData(String expression, Vec3 origin, Vec3 lookVector,
                       double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
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
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;
        public Vec3 lastPos;

        public RocketData(String expression, Vec3 origin, Vec3 lookVector,
                          double distance, int ticks, Vec3 lastPos) {
            this.expression = expression;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
            this.lastPos = lastPos;
        }
    }
}