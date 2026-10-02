package com.example.uselocalrecipes.config;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A small json config, implemented without any loader specific config api so both loaders share the file format.
 */
public class UseLocalRecipesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile UseLocalRecipesConfig instance;

    /** Whether this mod does anything at all. */
    public boolean enabled = true;

    /** Ticks to wait after joining a world before falling back to locally read recipes. */
    public int syncDelayTicks = 60;

    /**
     * Whether the recipes the server sent win over locally read ones. When disabled, local recipes of a
     * recipe type the server sent are added on top, which only helps when the server sends incomplete data.
     */
    public boolean preferServerTypes = true;

    /** Warns in chat when recipes had to be read from local files because the server did not send them. */
    public boolean warnAboutLocalRecipes = true;

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
        boolean writeBack = !Files.exists(path);

        if (!writeBack) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject raw = JsonParser.parseReader(reader).getAsJsonObject();
                UseLocalRecipesConfig read = GSON.fromJson(raw, UseLocalRecipesConfig.class);
                if (read != null) {
                    config = read;
                }
                // Options added since the file was written are missing from it, show them to the user.
                writeBack = !raw.keySet().containsAll(GSON.toJsonTree(config).getAsJsonObject().keySet());
            } catch (Exception e) {
                Constants.LOG.warn("Could not read {}, using default settings", path, e);
            }
        }

        config.syncDelayTicks = Math.max(0, config.syncDelayTicks);
        if (writeBack) {
            config.write(path);
        }
        instance = config;
        return config;
    }

    private void write(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Not every file system can move atomically, a plain replace beats a half written file.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Constants.LOG.warn("Could not write {}", path, e);
        }
    }
}
