package io.github.skm.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("defaultKey")
    @Mutable
    void skm$setDefaultKey(InputConstants.Key key);

    @Accessor("ALL")
    static Map<String, KeyMapping> skm$getAll() {
        throw new AssertionError();
    }

    @Accessor("MAP")
    static Map<InputConstants.Key, List<KeyMapping>> skm$getByKey() {
        throw new AssertionError();
    }
}
