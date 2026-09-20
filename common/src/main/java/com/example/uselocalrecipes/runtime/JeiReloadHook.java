package com.example.uselocalrecipes.runtime;

import com.example.uselocalrecipes.Constants;

/**
 * Asks JEI to rebuild its recipe list after local recipes were injected.
 *
 * <p>Fabric reloads JEI whenever recipes are synchronized, so nothing has to be done there.
 * NeoForge only restarts JEI when it sees both updated tags and updated recipes, so the start observer
 * is nudged directly through a mixin, which leaves itself inactive when JEI is not installed.
 */
public final class JeiReloadHook {

    private static Reloadable observer;

    private JeiReloadHook() {
    }

    /** Called by the mixin on JEI's start observer when it is created. */
    public static void capture(Reloadable instance) {
        observer = instance;
    }

    public static void requestReload() {
        Reloadable captured = observer;
        if (captured == null) {
            return;
        }

        try {
            captured.ulr$reloadRecipes();
        } catch (Throwable t) {
            Constants.LOG.warn("Could not ask JEI to reload its recipes", t);
        }
    }

    /** Implemented by the mixin on JEI's start observer, which is only present on NeoForge. */
    public interface Reloadable {

        void ulr$reloadRecipes();
    }
}
