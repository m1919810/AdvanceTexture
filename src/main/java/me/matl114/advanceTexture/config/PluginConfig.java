package me.matl114.advanceTexture.config;

import lombok.Data;

@Data
public class PluginConfig {
    public static final int CONFIG_VERSION = 1;
    int configVersion = CONFIG_VERSION;
    boolean autoUpdate = false;
    TextureConfig textureService = new TextureConfig();
    ItemModelConfig itemModelService = new ItemModelConfig();
    ServerPackageConfig resourceService = new ServerPackageConfig();
}
