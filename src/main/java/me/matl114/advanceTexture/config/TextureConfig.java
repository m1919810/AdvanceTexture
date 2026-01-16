package me.matl114.advanceTexture.config;

import lombok.Data;

import java.util.Map;

@Data
public class TextureConfig {
    boolean enableTexture = false;
    Map<String, String> itemModelPaths = Map.of(
        "slimefun", "item-models.yml"
    );
    Map<String, String> customIdPaths = Map.of(
        "slimefun", "PublicBukkitValues.slimefun:slimefun_item"
    );

}
