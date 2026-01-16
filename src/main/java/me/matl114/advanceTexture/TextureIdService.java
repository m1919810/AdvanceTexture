package me.matl114.advanceTexture;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import me.matl114.matlib.algorithms.algorithm.FileUtils;
import me.matl114.matlib.core.Manager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class TextureIdService implements Manager {
    AdvanceTexture plugin;
    @Getter
    private static TextureIdService manager;

    public TextureIdService(){
        manager = this;
    }

    @Override
    public boolean isAutoDisable() {
        return true;
    }

    @Override
    public <T extends Manager> T init(Plugin pl, String... path) {
        plugin = (AdvanceTexture) pl;
        addToRegistry();
        tryLoad();
        return (T) this;
    }
    private void tryLoad(){
        textureIds.clear();
        Map<String, String> paths = plugin.getPluginConfig().getTextureService().getItemModelPaths();
        for(var entry : paths.entrySet()) {
            String path = entry.getValue();
            if(path != null){
                File file;
                try{
                    String filePath = "plugins/" + plugin.getName().replace(" ", "_") + "/" + path;
                    file = new File(filePath);
                    if(!file.exists()){
                        FileUtils.copyFile(AdvanceTexture.class, path, filePath);
                    }

                }catch (IOException e){
                    throw new RuntimeException(e);
                }
                Map<String, ?> map0 = FileUtils.readFileYaml(file);
                if(map0 != null){
                    for(Map.Entry<String, ?> entry0 : map0.entrySet()){
                        if(entry0.getValue() instanceof Number number){
                            //add namespacedkey
                            textureIds.put(entry.getKey() + ":" + entry0.getKey(), (int) number);
                        }
                    }
                }

            }
        }

    }

    private final Object2IntMap<String> textureIds = new Object2IntOpenHashMap<String>();

    public int getTextureId(String texture){
        return textureIds.getInt(texture);
    }
    public int getTextureIdOr(String texture){
        return textureIds.getOrDefault(texture, 0);
    }

    @Override
    public <T extends Manager> T reload() {
        deconstruct();
        return init(plugin);
    }

    @Override
    public void deconstruct() {
        textureIds.clear();
        removeFromRegistry();
    }
}
