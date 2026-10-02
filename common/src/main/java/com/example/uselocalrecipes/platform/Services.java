package com.example.uselocalrecipes.platform;

import com.example.uselocalrecipes.Constants;
import com.example.uselocalrecipes.platform.services.IPlatformHelper;
import com.example.uselocalrecipes.platform.services.IRecipePlatform;

import java.util.ServiceLoader;

public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static final IRecipePlatform RECIPES = load(IRecipePlatform.class);

    /** Loads the platform implementation of a service, see the files in META-INF/services. */
    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
