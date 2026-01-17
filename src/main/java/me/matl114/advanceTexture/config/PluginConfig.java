package me.matl114.advanceTexture.config;

import lombok.Data;

@Data
public class PluginConfig {
    boolean autoUpdate = false;
    TextureConfig textureService = new TextureConfig();
    ItemModelConfig itemModelService = new ItemModelConfig();
    ServerPackageConfig resourceService = new ServerPackageConfig();
}
