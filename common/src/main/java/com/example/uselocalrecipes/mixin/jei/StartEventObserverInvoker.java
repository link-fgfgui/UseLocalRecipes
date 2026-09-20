package com.example.uselocalrecipes.mixin.jei;

import com.example.uselocalrecipes.runtime.JeiReloadHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes JEI's private restart method, see {@link StartEventObserverMixin} and {@link JeiReloadHook}.
 */
@Pseudo
@Mixin(targets = "mezz.jei.neoforge.startup.StartEventObserver")
public interface StartEventObserverInvoker extends JeiReloadHook.Reloadable {

    @Invoker("restart")
    @Override
    void ulr$reloadRecipes();
}
