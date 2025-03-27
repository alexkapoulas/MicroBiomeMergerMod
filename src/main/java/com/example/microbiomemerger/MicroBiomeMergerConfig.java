package com.example.microbiomemerger;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

public class MicroBiomeMergerConfig {
    public enum LogLevel { NONE, INFO, DEBUG }
    public enum VerticalMode { SURFACE, SEA_LEVEL, HIGHEST_BLOCK }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CONFIG_SPEC;

    public static final ForgeConfigSpec.IntValue MIN_BIOME_AREA;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PRIORITY_LIST;
    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_BIOME;
    public static final ForgeConfigSpec.BooleanValue enabled;
    public static final ForgeConfigSpec.EnumValue<LogLevel> LOG_LEVEL;
    public static final ForgeConfigSpec.EnumValue<VerticalMode> VERTICAL_SAMPLE;

    static {
        BUILDER.comment("Minimum contiguous biome area (in number of biome blocks) considered stable. Smaller areas will be merged.");
        MIN_BIOME_AREA = BUILDER.defineInRange("minBiomeArea", 50, 1, Integer.MAX_VALUE);

        BUILDER.comment("Biomes that should not be merged (by resource location).");
        BIOME_BLACKLIST = BUILDER.defineList("blacklist", List.of(), obj -> obj instanceof String);

        BUILDER.comment("Priority biome list: if a micro biome borders these biomes, those will be chosen in order of this list.");
        PRIORITY_LIST = BUILDER.defineList("priorityList", List.of(), obj -> obj instanceof String);

        BUILDER.comment("Fallback biome (resource location) to use if no suitable neighbor biome is found for merging.");
        FALLBACK_BIOME = BUILDER.define("fallbackBiome", "");

        // Master enable/disable toggle for the biome merging logic
        enabled = BUILDER.comment("Whether micro biome merging is enabled")
                .define("enabled", true);

        BUILDER.comment("Logging level for merge operations: NONE, INFO, DEBUG.");
        LOG_LEVEL = BUILDER.defineEnum("logLevel", LogLevel.INFO);

        BUILDER.comment("Vertical sampling mode: SURFACE (top terrain level), SEA_LEVEL (constant Y=63), HIGHEST_BLOCK (determine each column's top).");
        VERTICAL_SAMPLE = BUILDER.defineEnum("verticalSample", VerticalMode.SURFACE);

        CONFIG_SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, MicroBiomeMergerMod.MODID + ".toml");
    }

    public static void saveToFile() {
        // Cast the config object from the spec to CommentedFileConfig and then save it
        CommentedFileConfig file = (CommentedFileConfig) CONFIG_SPEC.getSpec();
        file.save();
    }



    public static void loadFromFile() {
        // Reload config values from disk
        CommentedFileConfig file = CommentedFileConfig.builder(FMLPaths.CONFIGDIR.get().resolve(MicroBiomeMergerMod.MODID + ".toml")).build();
        file.load();
        CONFIG_SPEC.setConfig(file);
    }
}
