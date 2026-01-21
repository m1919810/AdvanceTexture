package me.matl114.advanceTexture;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import me.matl114.advanceTexture.config.PluginConfig;
import me.matl114.matlib.algorithms.algorithm.FileUtils;
import me.matl114.matlib.core.Manager;
import me.matl114.matlib.core.UtilInitialization;
import me.matl114.matlib.utils.Debug;
import net.guizhanss.guizhanlibplugin.updater.GuizhanUpdater;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.logging.Level;

public class AdvanceTexture extends JavaPlugin {
    private UtilInitialization matlib;
    @Getter
    private static AdvanceTexture instance;
    @Getter
    private PacketEventsAPI<?> packetEventsAPI;
    @Getter
    private PluginConfig pluginConfig;
    @Getter
    private TextureIdService idService;
    @Getter
    private AdvanceTextureMainCommand command;
    @Getter
    private TextureTranslationService translator;
    @Getter
    private ResourcePackService resourceService;
    public void onEnable(){
        super.onEnable();
        instance = this;
        matlib = new UtilInitialization(this, "AdvanceTexture")
            .onEnable();
        loadPacketApi();
        loadConfig();
        autoUpdate();
        command = new AdvanceTextureMainCommand()
            .init(this);
        idService = new TextureIdService()
            .init(this);
        translator = new TextureTranslationService()
            .init(this);
        resourceService = new ResourcePackService()
            .init(this);

    }

    public void onDisable(){
        super.onDisable();
        matlib.onDisable();

        packetEventsAPI.terminate();
        resourceService = null;
        translator = null;
        idService = null;
        command = null;
        instance = null;

    }

    public void reload(){
        loadConfig();
        new HashSet<>(Manager.managers).forEach(Manager::reload);
    }


    private void loadPacketApi(){
        packetEventsAPI = SpigotPacketEventsBuilder.build(instance);
        PacketEvents.setAPI(packetEventsAPI);
        PacketEvents.getAPI().getSettings()
            .fullStackTrace(true)
            .kickOnPacketException(true)
            .checkForUpdates(false)
            .reEncodeByDefault(false)
            .debug(false);
        PacketEvents.getAPI().init();
    }

    private void loadConfig(){
        File file;
        String filePath = "plugins/" + this.getName().replace(" ", "_") + "/config.yml";
        try{
            file = new File(filePath);
            FileUtils.ensureParentDir(file);
            if(!file.exists()){
                FileUtils.copyFile(AdvanceTexture.class, "config.yml", filePath);
            }

        }catch (IOException e){
            throw new RuntimeException(e);
        }
        boolean needUpdate = false;
        try{
            pluginConfig = FileUtils.readFileYaml(file, PluginConfig.class);
            if(pluginConfig.getConfigVersion() < PluginConfig.CONFIG_VERSION){
                Debug.warn("Your config file is current outdated. Auto updating your config.");
                needUpdate = true;
            }
        }catch (Throwable e){
            //handle config format change
            Debug.warn("An error occurred while loading plugin config, Auto updating the config.yml to the latest format");
            needUpdate = true;
        }
        if(needUpdate){
            try{
                FileUtils.copyFile(AdvanceTexture.class, "config.yml", filePath);
                pluginConfig = FileUtils.readFileYaml(file, PluginConfig.class);
            }catch (Throwable e){
                throw new RuntimeException(e);
            }
        }
    }

    private static record RepoInfo(String userName, String repoUrl, String branch){

    }
    private static RepoInfo repo = new RepoInfo("m1919810", "AdvanceTexture", "master");

    private void autoUpdate(){
        if(pluginConfig.isAutoUpdate()){
            try{

                if (this.getDescription().getVersion().startsWith("Build")) {
                    GuizhanUpdater.start(this, this.getFile(), repo.userName, repo.repoUrl, repo.branch);
                }
            }catch (Throwable e){
                this.getLogger().log(Level.WARNING, "本插件的自动更新无法启动: {0}" ,e.getMessage());
            }
        }
    }
}
