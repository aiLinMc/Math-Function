package com.ailinmc.function_math.command;

import org.checkerframework.checker.units.qual.s;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

import com.ailinmc.function_math.procedures.FunctionBlockStandProcedure;
import com.ailinmc.function_math.procedures.FunctionBlockFloorProcedure;

@EventBusSubscriber
public class FxCommand {
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher()
				.register(Commands.literal("fx").requires(s -> s.hasPermission(3))
						.then(Commands.argument("analyticExpression", StringArgumentType.string())
								.then(Commands.argument("functionValueMin", DoubleArgumentType.doubleArg())
										.then(Commands.argument("functionValueMax", DoubleArgumentType.doubleArg()).then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
												.then(Commands.argument("scale", DoubleArgumentType.doubleArg(1, 10)).then(Commands.literal("stand").then(Commands.argument("coordinateSystem", BoolArgumentType.bool()).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockStandProcedure.execute(arguments);
													return 0;
												})).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockStandProcedure.execute(arguments);
													return 0;
												})).then(Commands.literal("floor").then(Commands.argument("coordinateSystem", BoolArgumentType.bool()).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockFloorProcedure.execute(world, x, y, z, arguments);
													return 0;
												})).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockStandProcedure.execute(arguments);
													return 0;
												})).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockStandProcedure.execute(arguments);
													return 0;
												})).executes(arguments -> {
													Level world = arguments.getSource().getUnsidedLevel();
													double x = arguments.getSource().getPosition().x();
													double y = arguments.getSource().getPosition().y();
													double z = arguments.getSource().getPosition().z();
													Entity entity = arguments.getSource().getEntity();
													if (entity == null && world instanceof ServerLevel _servLevel)
														entity = FakePlayerFactory.getMinecraft(_servLevel);
													Direction direction = Direction.DOWN;
													if (entity != null)
														direction = entity.getDirection();

													FunctionBlockStandProcedure.execute(arguments);
													return 0;
												}))))));
	}

}