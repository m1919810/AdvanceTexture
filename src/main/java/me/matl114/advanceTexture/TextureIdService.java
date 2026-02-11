package me.matl114.advanceTexture;

import com.github.retrooper.packetevents.resources.ResourceLocation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import me.matl114.matlib.algorithms.algorithm.FileUtils;
import me.matl114.matlib.core.Manager;
import me.matl114.matlib.utils.Debug;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
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
        Map<String, String> paths = new LinkedHashMap<>();
        if(plugin.getPluginConfig().getTextureService().isEnableTexture()){
            paths.putAll(plugin.getPluginConfig().getTextureService().getItemModelPaths());
        }
        if(plugin.getPluginConfig().getItemModelService().isEnableModel()){
            paths.putAll(plugin.getPluginConfig().getItemModelService().getItemModelPaths());
        }
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
                    Map<String, ?> map0 = FileUtils.readFileYaml(file);
                    if(map0 != null){
                        for(Map.Entry<String, ?> entry0 : map0.entrySet()){
                            if(entry0.getValue() instanceof Number number){
                                //add namespacedkey
                                textureIds.put(entry.getKey() + ":" + entry0.getKey(), (int) number);
                            }else if(entry0.getValue() instanceof String str){
                                try{
                                    textureModelPath.put(entry.getKey() + ":" + entry0.getKey(), new ResourceLocation(str));
                                }catch (Throwable e){
                                    Debug.logger("Invalid model path: " + str);
                                }

                            }
                        }
                    }
                }catch (IOException e){
                    Debug.logger("Fail to load texture id file :" , path);
                }


            }
        }

    }

    private final Object2IntMap<String> textureIds = new Object2IntOpenHashMap<String>();
    private final Object2ObjectMap<String, ResourceLocation> textureModelPath = new Object2ObjectOpenHashMap<>();

    public int getTextureId(String texture){
        return textureIds.getInt(texture);
    }

    public ResourceLocation getTextureModel(String texture){
        return textureModelPath.get(texture);
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
