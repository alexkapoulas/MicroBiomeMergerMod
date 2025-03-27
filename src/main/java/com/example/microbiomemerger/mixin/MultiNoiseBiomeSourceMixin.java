package com.example.microbiomemerger.mixin;

import com.example.microbiomemerger.ExecutionTimeProfiler;
import com.example.microbiomemerger.MicroBiomeMergerConfig;
import com.example.microbiomemerger.MicroBiomeMergerEngine;
import com.example.microbiomemerger.MicroBiomeMergerMod;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {
    private static final Logger LOGGER = LogManager.getLogger();

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("TAIL"), cancellable = true)
    private void onGetNoiseBiome(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        // If mod functionality is disabled via config, do nothing
        if (!MicroBiomeMergerConfig.enabled.get()) {
            return;
        }

        long startTime = System.nanoTime();
        Holder<Biome> original = cir.getReturnValue();
        
        Holder<Biome> modified = MicroBiomeMergerEngine.getMergedBiome((MultiNoiseBiomeSource)(Object)this, x, y, z, original, sampler);
        // Holder<Biome> modified = MicroBiomeMergerEngine.getMergedBiomeWithTimeout((MultiNoiseBiomeSource)(Object)this, x, y, z, original, sampler);
        if (modified != null && modified != original) {
            cir.setReturnValue(modified);
        }
        long endTime = System.nanoTime();
        MicroBiomeMergerMod.profiler.recordExecutionTime("MultiNoiseBiomeSource#getNoiseBiome", endTime - startTime);
    }
}
