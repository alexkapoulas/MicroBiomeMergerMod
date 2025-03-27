package com.example.microbiomemerger;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MicroBiomeMergerMod.MODID)
public class MicroBiomeMergerMod {
    public static final String MODID = "microbiomemerger";
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ExecutionTimeProfiler profiler = new ExecutionTimeProfiler();
    private boolean loadedLevels = false;


    public MicroBiomeMergerMod() {
        // Register config
        MicroBiomeMergerConfig.register();
        // Register event listeners on the Forge event bus
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Map MultiNoiseBiomeSource instances to their levels for height lookup, etc.
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            MicroBiomeMergerEngine.registerBiomeSource(level);
        }
        loadedLevels = true;
        LOGGER.info("MicroBiomeMerger: Mapped biome sources for {} worlds during ServerStartedEvent onServerStarted", server.getAllLevels().spliterator().getExactSizeIfKnown());
    }

    @SubscribeEvent
    public void load(LevelEvent event) {
        if (loadedLevels) {
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            MicroBiomeMergerEngine.registerBiomeSource(level);
        }
        LOGGER.info("MicroBiomeMerger: Mapped biome sources for {} worlds during LevelEvent load", server.getAllLevels().spliterator().getExactSizeIfKnown());
    }
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Register the /microbiome command with subcommands
        event.getDispatcher().register(
                Commands.literal("microbiome")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("enabled")
                                .executes(ctx -> {
                                    boolean current = MicroBiomeMergerConfig.enabled.get();
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("MicroBiomeMerger " + (current ? "enabled" : "disabled")),
                                            false
                                    );
                                    return 1;
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            MicroBiomeMergerConfig.enabled.set(enabled);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("MicroBiomeMerger " + (enabled ? "enabled" : "disabled")),
                                                    false
                                            );
                                            LOGGER.info("MicroBiomeMerger enabled set to {}", enabled);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("queryprofiler")
                                .executes(ctx -> {
                                    double average = profiler.getAverage();
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Average execution time: " + average / 1000 + " µs"),
                                            false
                                    );
                                    return 1;
                                })
                                .then(Commands.argument("percentile", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(ctx -> {
                                            double percentile = DoubleArgumentType.getDouble(ctx, "percentile");
                                            long value = profiler.getValueAtPercentile(percentile);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Value at " + percentile + " percentile: " + value / 1000 + " µs"),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
