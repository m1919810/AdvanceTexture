package me.matl114.advanceTexture.config;

import lombok.Data;

@Data
public class PluginConfig {
    boolean autoUpdate = false;
    TextureConfig textureService = new TextureConfig();
    ServerPackageConfig resourceService = new ServerPackageConfig();
}
