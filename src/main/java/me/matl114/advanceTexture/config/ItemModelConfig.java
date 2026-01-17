package me.matl114.advanceTexture.config;

import lombok.Data;

import java.util.Map;

@Data
public class ItemModelConfig {
    boolean enableModel;
    Map<String, String> itemModelPaths = Map.of(
        "slimefun", "item-models.yml"
    );
    Map<String, String> ccustomModelPaths = Map.of(
        "slimefun", "PublicBukkitValues.slimefun:slimefun_item"
    );
}
