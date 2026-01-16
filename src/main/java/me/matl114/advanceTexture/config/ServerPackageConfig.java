package me.matl114.advanceTexture.config;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ServerPackageConfig {
    boolean enableServerResourcePack = false;

    boolean forceEnablePack = false;

    Map<String, PackEntryConfig> resourcePackUrl = Map.of();

    ServerPackageServiceConfig service = new ServerPackageServiceConfig();



}
