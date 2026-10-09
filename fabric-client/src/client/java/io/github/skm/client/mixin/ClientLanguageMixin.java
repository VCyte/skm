package io.github.skm.client.mixin;

import io.github.skm.client.ActionKeyBindings;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies SKM's dynamic labels without mutating Minecraft's immutable translation storage. */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    @Inject(
            method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skm$lookupDynamicTranslation(String key, String fallback,
                                                     CallbackInfoReturnable<String> cir) {
        String translation = ActionKeyBindings.translationFor(key);
        if (translation != null) {
            cir.setReturnValue(translation);
        }
    }
}
