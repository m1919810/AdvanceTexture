package me.matl114.advanceTexture.config;

import lombok.Data;

import java.util.Map;

@Data
public class ItemModelConfig {
    boolean enableModel;
    Map<String, String> itemModelPaths = Map.of(
        "slimefun", "item-model-overrides.yml"
    );
    Map<String, String> customModelPaths = Map.of(
        "slimefun", "PublicBukkitValues.slimefun:slimefun_item"
    );
}
