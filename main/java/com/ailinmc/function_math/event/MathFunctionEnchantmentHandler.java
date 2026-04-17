package com.ailinmc.function_math.event;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("^(f\\(x\\)=|y=)(.+)$");
    private static final double HORIZONTAL_SPEED = 1.0;
    private static final double MAX_DISTANCE = 1000;
    private static final int MAX_TICKS = 30 * 20;
    private static final double MAX_Y = 320;
    private static final double MIN_Y = -64;
    private static final double MAX_VERTICAL_SPEED = 2.5;
    private static final double MULTISHOT_OFFSET = 0.4;
    private static final int TNT_MAX_TICKS = 300;
    private static final double TNT_MAX_DISTANCE = 800.0;
    private static final double DELTA_X_FOR_DERIVATIVE = 0.001;
    private static final double TRAJECTORY_SCALE = 2.0;

    private static Method getPickupItemMethod = null;

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
    }

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new MathFunctionEnchantmentHandler());
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
        // 处理箭矢
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
                        shooter.position(),
                        horizontalDir,
                        0.0,
                        0
                    );
                    projectileDataMap.put(projectile, data);
                    com.ailinmc.function_math.FunctionMathMod.LOGGER.info("启用函数轨迹: " + expression + ", 方向: " + horizontalDir);
                }
            }
        }

        // 处理 TNT（打火石点燃）
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

                TNTData data = new TNTData(
                    expression,
                    igniter.position(),
                    horizontalDir,
                    0.0,
                    0
                );
                tntDataMap.put(tnt, data);
                com.ailinmc.function_math.FunctionMathMod.LOGGER.info("启用 TNT 函数轨迹: " + expression);
            }
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

        // 处理箭矢
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

        // 处理 TNT
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
    }

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
            Vec3 currentPos = projectile.position();
            Vec3 origin = data.origin;
            Vec3 lookVector = data.lookVector;
            double dx = (currentPos.x - origin.x) * lookVector.x + (currentPos.z - origin.z) * lookVector.z;
            data.projectedDistance = Math.max(0, dx);

            // 关键修复：将起始高度设置为 f(0)
            try {
                double f0 = ExpressionEvaluator.evaluate(data.expression, 0.0);
                double targetY = origin.y + f0;
                // 限制高度范围
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                // 直接设置实体位置
                projectile.setPos(projectile.getX(), targetY, projectile.getZ());
                // 同时清空垂直速度，避免后续突变
                Vec3 motion = projectile.getDeltaMovement();
                projectile.setDeltaMovement(motion.x, 0, motion.z);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("计算起始高度失败", e);
                return true;
            }
            return false;
        }

        data.projectedDistance += HORIZONTAL_SPEED;
        double xActual = data.projectedDistance;
        double xMath = xActual / TRAJECTORY_SCALE;

        double derivativeMath;
        try {
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("表达式在x=" + xMath + "处无效");
                return true;
            }
            derivativeMath = derivative(data.expression, xMath);
            derivativeMath = Math.min(MAX_VERTICAL_SPEED / HORIZONTAL_SPEED, Math.max(-MAX_VERTICAL_SPEED / HORIZONTAL_SPEED, derivativeMath));
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("表达式计算错误", e);
            return true;
        }

        double verticalSpeed = HORIZONTAL_SPEED * derivativeMath / TRAJECTORY_SCALE;
        Vec3 lookVector = data.lookVector;
        Vec3 horizontalMotion = lookVector.scale(HORIZONTAL_SPEED);
        Vec3 newMotion = new Vec3(horizontalMotion.x, verticalSpeed, horizontalMotion.z);
        projectile.setDeltaMovement(newMotion);
        projectile.hasImpulse = true;

        return false;
    }

    private boolean updateTNTMovement(PrimedTnt tnt, TNTData data) {
        if (data.firstTick) {
            data.firstTick = false;
            Vec3 currentPos = tnt.position();
            Vec3 origin = data.origin;
            Vec3 lookVector = data.lookVector;
            double dx = (currentPos.x - origin.x) * lookVector.x + (currentPos.z - origin.z) * lookVector.z;
            data.projectedDistance = Math.max(0, dx);

            // 关键修复：将起始高度设置为 f(0)
            try {
                double f0 = ExpressionEvaluator.evaluate(data.expression, 0.0);
                double targetY = origin.y + f0;
                targetY = Math.min(MAX_Y, Math.max(MIN_Y, targetY));
                tnt.setPos(tnt.getX(), targetY, tnt.getZ());
                Vec3 motion = tnt.getDeltaMovement();
                tnt.setDeltaMovement(motion.x, 0, motion.z);
            } catch (Exception e) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.error("TNT 计算起始高度失败", e);
                return true;
            }
            return false;
        }

        data.projectedDistance += HORIZONTAL_SPEED;
        double xActual = data.projectedDistance;
        double xMath = xActual / TRAJECTORY_SCALE;

        double derivativeMath;
        try {
            double rawY = ExpressionEvaluator.evaluate(data.expression, xMath);
            if (Double.isNaN(rawY) || Double.isInfinite(rawY)) {
                com.ailinmc.function_math.FunctionMathMod.LOGGER.warn("TNT 表达式在x=" + xMath + "处无效");
                return true;
            }
            derivativeMath = derivative(data.expression, xMath);
            derivativeMath = Math.min(MAX_VERTICAL_SPEED / HORIZONTAL_SPEED, Math.max(-MAX_VERTICAL_SPEED / HORIZONTAL_SPEED, derivativeMath));
        } catch (Exception e) {
            com.ailinmc.function_math.FunctionMathMod.LOGGER.error("TNT 表达式计算错误", e);
            return true;
        }

        double verticalSpeed = HORIZONTAL_SPEED * derivativeMath / TRAJECTORY_SCALE;
        Vec3 lookVector = data.lookVector;
        Vec3 horizontalMotion = lookVector.scale(HORIZONTAL_SPEED);
        Vec3 newMotion = new Vec3(horizontalMotion.x, verticalSpeed, horizontalMotion.z);
        tnt.setDeltaMovement(newMotion);
        tnt.hasImpulse = true;

        return false;
    }

    private boolean checkCollisionAndExplode(PrimedTnt tnt) {
        Level level = tnt.level();
        AABB boundingBox = tnt.getBoundingBox();
        Vec3 motion = tnt.getDeltaMovement();
        if (motion.lengthSqr() > 0.0001) {
            AABB newBox = boundingBox.move(motion);
            if (!level.noCollision(tnt, newBox) || level.containsAnyLiquid(newBox)) {
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

    private String extractExpression(String displayName) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(displayName);
        if (matcher.matches()) {
            return matcher.group(2).trim();
        }
        return "";
    }

    private static class ProjectileData {
        public final String expression;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;

        public ProjectileData(String expression, Vec3 origin, Vec3 lookVector, double distance, int ticks) {
            this.expression = expression;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
        }
    }

    private static class TNTData {
        public final String expression;
        public final Vec3 origin;
        public final Vec3 lookVector;
        public double projectedDistance;
        public int ticks;
        public boolean firstTick;

        public TNTData(String expression, Vec3 origin, Vec3 lookVector, double distance, int ticks) {
            this.expression = expression;
            this.origin = origin;
            this.lookVector = lookVector;
            this.projectedDistance = distance;
            this.ticks = ticks;
            this.firstTick = true;
        }
    }
}