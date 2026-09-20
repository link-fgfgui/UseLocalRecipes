package com.example.uselocalrecipes.config;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A small json config, deliberately implemented without any loader specific config api so that both
 * loaders share the exact same file format.
 */
public class UseLocalRecipesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UseLocalRecipesConfig instance;

    /** Whether this mod does anything at all. */
    public boolean enabled = true;

    /** Ticks to wait after joining a world before falling back to locally read recipes. */
    public int syncDelayTicks = 60;

    /**
     * Whether the recipes the server sent win over locally read recipes. When enabled, recipes of a
     * recipe type the server sent are taken from the server only. When disabled, locally read recipes
     * are added on top, which is only useful when the server sends incomplete data.
     */
    public boolean preferServerTypes = true;

    /** Logs every local recipe file that could not be parsed. */
    public boolean logFailedRecipes = false;

    /** Allows JEI to transfer recipes even when JEI is not installed on the server. */
    public boolean clientSideTransfer = true;

    public static UseLocalRecipesConfig get() {
        UseLocalRecipesConfig config = instance;
        return config != null ? config : load();
    }

    public static synchronized UseLocalRecipesConfig load() {
        Path path = Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json");
        UseLocalRecipesConfig config = new UseLocalRecipesConfig();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                UseLocalRecipesConfig read = GSON.fromJson(reader, UseLocalRecipesConfig.class);
                if (read != null) {
                    config = read;
                }
            } catch (Exception e) {
                Constants.LOG.warn("Could not read {}, using default settings", path, e);
            }
        } else {
            config.write(path);
        }

        config.syncDelayTicks = Math.max(0, config.syncDelayTicks);
        instance = config;
        return config;
    }

    private void write(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            Constants.LOG.warn("Could not write {}", path, e);
        }
    }
}
