package me.matl114.advanceTexture.config;

import lombok.Data;

import java.util.List;

@Data
public class ServerPackageServiceConfig {
    boolean enable = false;
    int port = 8080;
    List<String> resourcePackPath = List.of("generated.zip");
    boolean headerCheck = false;
}
