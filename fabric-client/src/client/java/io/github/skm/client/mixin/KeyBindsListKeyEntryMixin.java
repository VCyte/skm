package io.github.skm.client.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla Controls from hiding collisions just because both mappings still use their defaults. */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsListKeyEntryMixin {
    @Redirect(
            method = "refreshEntry",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDefault()Z", ordinal = 1)
    )
    private boolean skm$showDefaultSkmCollisionWithOther(KeyMapping otherKey) {
        return !isSkmMapping(otherKey) && otherKey.isDefault();
    }

    @Redirect(
            method = "refreshEntry",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDefault()Z", ordinal = 2)
    )
    private boolean skm$showDefaultSkmCollisionWithCurrent(KeyMapping currentKey) {
        return !isSkmMapping(currentKey) && currentKey.isDefault();
    }

    private static boolean isSkmMapping(KeyMapping key) {
        return key.getName().startsWith("key.skm.");
    }
}
