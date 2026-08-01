package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> SCANNING = SOUNDS.register(
        "scanning",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "scanning"))
    );
    public static final RegistrySupplier<SoundEvent> SCAN_COMPLETE = SOUNDS.register(
        "scan_complete",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "scan_complete"))
    );

    private ModSounds() {
    }

    public static void register() {
        SOUNDS.register();
    }
}
