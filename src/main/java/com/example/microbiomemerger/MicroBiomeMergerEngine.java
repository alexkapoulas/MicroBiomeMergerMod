package com.example.microbiomemerger;

import java.util.*;
import java.util.concurrent.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate;

public class MicroBiomeMergerEngine {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private static final ConcurrentMap<MultiNoiseBiomeSource, ServerLevel> sourceLevelMap = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<Long>> processedPositions = ThreadLocal.withInitial(HashSet::new);
    private static final ConcurrentMap<Long, Holder<Biome>> stableMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Holder<Biome>> microMap = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> scanning = ThreadLocal.withInitial(() -> false);

    public static void registerBiomeSource(ServerLevel level) {
        if (level.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource multiNoise) {
            sourceLevelMap.put(multiNoise, level);
        }
    }

    public static Holder<Biome> getMergedBiome(MultiNoiseBiomeSource biomeSource, int x, int y, int z, Holder<Biome> originalHolder, Climate.Sampler sampler) {
        // Check blacklist
        ResourceLocation biomeRL = originalHolder.unwrapKey().map(ResourceKey::location).orElse(null);
        if (biomeRL == null) {
            // fallback to Forge registry key
            ResourceKey<Biome> key = ForgeRegistries.BIOMES.getResourceKey(originalHolder.value()).orElse(null);
            if (key != null) biomeRL = key.location();
        }
        List<? extends String> blacklist = MicroBiomeMergerConfig.BIOME_BLACKLIST.get();
        if (biomeRL != null && blacklist.contains(biomeRL.toString())) {
            //LOGGER.debug("Biome {} is blacklisted, skipping merge.", biomeRL);
            return null;
        }

        //sourceLevelMap.forEach((key, value) -> LOGGER.info("Biome source: " + key + " Level: " + value));
        ServerLevel level = sourceLevelMap.get(biomeSource);

        if (level == null) {
            LOGGER.error("MicroBiomeMerger: Could not find ServerLevel for MultiNoiseBiomeSource.");
            return null;
        }

        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

        int sampleY = level.getMinBuildHeight() + level.getHeight() / 2;

        BlockPos startPos = new BlockPos(x, sampleY, z);

        // if (sampleY > -1000) { return null; }
//        if (processedPositions.get().size() % 10 == 0) {
//            LOGGER.info("Processed positions: {} Scanning: {}", processedPositions.get().size(), scanning.get());
//        }

        if (scanning.get()) {
            return null;
        }
        scanning.set(true);
        try {
            Holder<Biome> regionBiome = biomeSource.getNoiseBiome(x, sampleY, z, sampler);
            if (!regionBiome.equals(originalHolder)) {
                return null;
            }

            // Check caches
            Holder<Biome> cachedOverride = stableMap.get(encode(x, z));
            if (cachedOverride != null) {
                return cachedOverride;
            }
            cachedOverride = microMap.get(encode(x, z));
            if (cachedOverride != null) {
                return cachedOverride;
            }

            // Scan the region for biome patches
            RegionBoundaryResult scanResult = scanRegion(biomeSource, x, sampleY, z, originalHolder, sampler);
            if (scanResult.region.isEmpty() && !scanResult.stable) {
                // LOGGER.info("MicroBiomeMerger: No region found at ({}, {})", x, z);
                return null;
            }
            else if (scanResult.stable) {
                //LOGGER.info("MicroBiomeMerger: Stable region at ({}, {}) of size {}", x, z , scanResult.region.size());
                scanResult.region.forEach(pos -> stableMap.put(pos, regionBiome));
                return null;
            }
            else {
                //LOGGER.info("MicroBiomeMerger: Merged region at ({}, {}) of size {}", x, z , scanResult.region.size());
                // Largest neighbor region by boundary count
                int maxCount = -1;
                int totalCount = 0;
                Holder<Biome> dominantBiome = null;
                for (Map.Entry<ResourceKey<Biome>, Integer> entry : scanResult.boundary.entrySet()) {
                    totalCount += entry.getValue();
                    if (entry.getValue() > maxCount) {
                        maxCount = entry.getValue();
                        dominantBiome = biomeRegistry.getHolderOrThrow(entry.getKey());
                    }
                }
                //dominantBiome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DEEP_DARK);

                for (long pos : scanResult.region) {
                    microMap.put(pos, dominantBiome);
                }
                // LOGGER.info("MicroBiomeMerger: {} -> {} ({} {}/{})", originalHolder.unwrapKey().map(ResourceKey::location).orElse(null), dominantBiome.unwrapKey().map(ResourceKey::location).orElse(null), scanResult.region.size(), maxCount, totalCount);
                LOGGER.info("MicroBiomeMerger: Merged biome {} -> {} at ({}, {}, {}) of size {}", originalHolder.unwrapKey().map(ResourceKey::location).orElse(null), dominantBiome.unwrapKey().map(ResourceKey::location).orElse(null), x, y, z, scanResult.region.size());

                return dominantBiome;
            }
            
        } finally {
            scanning.set(false);
        }
    }


    private static RegionBoundaryResult scanRegion(MultiNoiseBiomeSource biomeSource, int x, int sampleY, int z,
                                                   Holder<Biome> originalHolder, Climate.Sampler sampler) {
        Set<Long> region = new HashSet<>();
        Map<ResourceKey<Biome>, Integer> boundary = new HashMap<>();

        // Queue for BFS; using int[] to represent (x, z) coordinates.
        Queue<int[]> queue = new LinkedList<>();
        // Set to track visited positions.
        Set<Long> visited = new HashSet<>();

        // Start from the given (x,z) coordinate.
        long startPos = encode(x, z);
        queue.add(new int[]{x, z});
        visited.add(startPos);

        // Define the four cardinal directions.
        int[][] directions = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };

        int steps = 0;
        while (!queue.isEmpty()) {
            steps += 1;
            int[] pos = queue.poll();
            int currX = pos[0];
            int currZ = pos[1];
            long posEncoded = encode(currX, currZ);

            // Sample the biome at the current coordinate.
            Holder<Biome> currentBiome = biomeSource.getNoiseBiome(currX, sampleY, currZ, sampler);

            if (stableMap.containsKey(posEncoded)) {
                Holder<Biome> cachedBiome = stableMap.get(posEncoded);
                if (cachedBiome.equals(originalHolder)) {
                    // LOGGER.info("Found a matching stable region at ({}, {})", currX, currZ);
                    return new RegionBoundaryResult(region, boundary, true);
                }
                else {
                    // LOGGER.info("Found a different cached biome at ({}, {}): {} -> {}", currX, currZ, currentBiome.unwrapKey().map(ResourceKey::location).orElse(null), cachedBiome.unwrapKey().map(ResourceKey::location).orElse(null));
                    if (!currentBiome.equals(cachedBiome)) {
                        LOGGER.info("Cache differs from world gen");
                    }
                    //currentBiome = cachedBiome;
                }
            }

            if (region.size() >= MicroBiomeMergerConfig.MIN_BIOME_AREA.get())
            {
                return new RegionBoundaryResult(region, boundary, true);
            }
            if (currentBiome.equals(originalHolder)) {
                // Add to the contiguous region.
                region.add(posEncoded);
                // Enqueue all unvisited neighbors.
                for (int[] dir : directions) {
                    int neighborX = currX + dir[0];
                    int neighborZ = currZ + dir[1];
                    long neighborEncoded = encode(neighborX, neighborZ);
                    if (!visited.contains(neighborEncoded)) {
                        visited.add(neighborEncoded);
                        queue.add(new int[]{neighborX, neighborZ});
                    }
                }
            } else {
                // Record as a boundary cell.
                Holder<Biome> finalCurrentBiome = currentBiome;
                ResourceKey<Biome> boundaryKey = currentBiome.unwrapKey().orElseGet(() -> ForgeRegistries.BIOMES.getResourceKey(finalCurrentBiome.value()).orElse(null));

                if (boundaryKey != null) {
                    boundary.merge(boundaryKey, 1, Integer::sum);
                }
            }
        }

        // LOGGER.info("Total queue steps: {}", steps);
        return new RegionBoundaryResult(region, boundary, false);
    }

    /**
     * Helper method to encode an (x,z) pair into a single long.
     * Common approach: lower 32 bits are x, upper 32 bits are z.
     */
    private static long encode(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    // Helper class to store the result of the flood-fill.
        public record RegionBoundaryResult(Set<Long> region, Map<ResourceKey<Biome>, Integer> boundary, boolean stable) {
    }
}