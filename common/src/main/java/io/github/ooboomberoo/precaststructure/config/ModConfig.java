package io.github.ooboomberoo.precaststructure.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON config at {@code config/precast_structure.json}.
 * Editable in-game via Cloth Config through Mod Menu (Fabric) or the NeoForge mods list.
 * Values are also written back to the JSON file when saved from the GUI.
 */
public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrecastStructureMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "precast_structure.json";

    private static ModConfig instance = new ModConfig();

    public String _comment = "Precast Structure config. Restart the game after editing.";

    public Scanning scanning = new Scanning();
    public Deploy deploy = new Deploy();
    public Frame frame = new Frame();
    public Blueprint blueprint = new Blueprint();
    public Printer printer = new Printer();
    public Hologram hologram = new Hologram();

    public static final class Scanning {
        /** Minimum scan animation length in ticks. */
        public int minTicks = 80;
        /** Extra scan ticks per block of frame height. */
        public int ticksPerHeight = 16;
        /** How often the scanning loop sound plays (ticks). */
        public int soundIntervalTicks = 5;
    }

    public static final class Deploy {
        /** When false, pre-cast structures place instantly with no rising-plane animation. */
        public boolean animated = true;
        /** Minimum deploy animation length in ticks. */
        public int minTicks = 80;
        /** Extra deploy ticks per block of structure height. */
        public int ticksPerHeight = 16;
        /** How often the deploy loop sound plays (ticks). */
        public int soundIntervalTicks = 5;
        /** Client ticks to keep fullbright solid cover after place while lighting catches up. */
        public int clientGraceTicks = 8;
    }

    public static final class Frame {
        /** Minimum platform width/depth (inclusive). */
        public int minPlatformSize = 3;
        /** Maximum platform width/depth/height (inclusive). */
        public int maxPlatformSize = 64;
    }

    public static final class Blueprint {
        /** Hard clamp for blueprint size axes when loading NBT. */
        public int maxDimension = 256;
    }

    public static final class Printer {
        /**
         * Fallback print duration in ticks when the world gamerule is unavailable.
         * Live worlds still use the {@code precastStructurePrinterDelay} gamerule.
         */
        public int defaultDelayTicks = 100;
        /** How often the printing loop sound plays (ticks). */
        public int soundIntervalTicks = 5;
    }

    public static final class Hologram {
        /** When true, scan/deploy holograms use invisible solid collision cubes. */
        public boolean solidCollision = true;
    }

    private ModConfig() {
    }

    public static ModConfig get() {
        return instance;
    }

    public static void load() {
        Path path = Platform.getConfigFolder().resolve(FILE_NAME);
        ModConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        if (loaded == null) {
            loaded = new ModConfig();
        }
        loaded.sanitize();
        instance = loaded;
        save();
    }

    public static void save() {
        instance.sanitize();
        Path path = Platform.getConfigFolder().resolve(FILE_NAME);
        try {
            instance.write(path);
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", path, e);
        }
    }

    private void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    private void sanitize() {
        if (scanning == null) {
            scanning = new Scanning();
        }
        if (deploy == null) {
            deploy = new Deploy();
        }
        if (frame == null) {
            frame = new Frame();
        }
        if (blueprint == null) {
            blueprint = new Blueprint();
        }
        if (printer == null) {
            printer = new Printer();
        }
        if (hologram == null) {
            hologram = new Hologram();
        }
        if (_comment == null || _comment.isBlank()) {
            _comment = "Precast Structure config. Restart the game after editing.";
        }

        scanning.minTicks = Mth.clamp(scanning.minTicks, 1, 20_000);
        scanning.ticksPerHeight = Mth.clamp(scanning.ticksPerHeight, 0, 200);
        scanning.soundIntervalTicks = Mth.clamp(scanning.soundIntervalTicks, 1, 200);

        deploy.minTicks = Mth.clamp(deploy.minTicks, 1, 20_000);
        deploy.ticksPerHeight = Mth.clamp(deploy.ticksPerHeight, 0, 200);
        deploy.soundIntervalTicks = Mth.clamp(deploy.soundIntervalTicks, 1, 200);
        deploy.clientGraceTicks = Mth.clamp(deploy.clientGraceTicks, 0, 100);

        frame.minPlatformSize = Mth.clamp(frame.minPlatformSize, 1, 256);
        frame.maxPlatformSize = Mth.clamp(frame.maxPlatformSize, frame.minPlatformSize, 256);

        blueprint.maxDimension = Mth.clamp(blueprint.maxDimension, 1, 512);
        printer.defaultDelayTicks = Mth.clamp(printer.defaultDelayTicks, 1, 20_000);
        printer.soundIntervalTicks = Mth.clamp(printer.soundIntervalTicks, 1, 200);
    }
}
