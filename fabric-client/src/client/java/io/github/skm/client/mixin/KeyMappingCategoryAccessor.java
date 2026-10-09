package io.github.skm.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes Minecraft's mutable category order list so SKM can be pinned to the first position. */
@Mixin(KeyMapping.Category.class)
public interface KeyMappingCategoryAccessor {
    @Accessor("SORT_ORDER")
    static List<KeyMapping.Category> skm$getSortOrder() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
