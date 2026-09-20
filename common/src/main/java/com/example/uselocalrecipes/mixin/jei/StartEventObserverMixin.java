package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.runtime.JeiReloadHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures JEI's start observer so that {@link JeiReloadHook} can restart JEI when recipes changed after
 * JEI already loaded its recipes. The target only exists in JEI's NeoForge build, on Fabric JEI reloads
 * on its own whenever recipes are synchronized, so this mixin simply stays inactive there.
 *
 * <p>{@link Pseudo} keeps this mixin harmless when JEI is not installed.
 */
@Pseudo
@Mixin(targets = "mezz.jei.neoforge.startup.StartEventObserver")
public class StartEventObserverMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ulr$captureInstance(CallbackInfo ci) {
        Object self = this;
        if (self instanceof JeiReloadHook.Reloadable reloadable) {
            JeiReloadHook.capture(reloadable);
        }
    }
}
