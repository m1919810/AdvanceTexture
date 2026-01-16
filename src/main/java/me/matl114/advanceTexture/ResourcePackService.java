package me.matl114.advanceTexture;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.matl114.advanceTexture.config.PackEntryConfig;
import me.matl114.advanceTexture.config.ServerPackageServiceConfig;
import me.matl114.matlib.algorithms.algorithm.FileUtils;
import me.matl114.matlib.core.Manager;
import me.matl114.matlib.utils.Debug;
import me.matl114.matlib.utils.version.Version;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class ResourcePackService implements Manager , Listener {
    AdvanceTexture plugin;
    boolean enableTextureLoad;
    boolean forceEnableTextureLoad;
    Map<String, PackEntryConfig> urls = new LinkedHashMap<>();
    private final Map<String, byte[]> hashes = new ConcurrentHashMap<>();
    ServerPackageServiceConfig serviceConfig;
    boolean register;
    Service httpServer;
    @Override
    public boolean isAutoDisable() {
        return true;
    }
    public ResourcePackService()
    {
        manager = this;
    }

    @Getter
    private static ResourcePackService manager;
    public Collection<String> getResourcePackNames(){
        return Collections.unmodifiableCollection(urls.keySet());
    }


    public boolean loadResourcePack(Player p, String packName){
        PackEntryConfig urlE = urls.get(packName);
        if(urlE == null)return false;
        String url = urlE.getUrl();
        //nullable
        byte[] digest = hashes.get(urlE.getLocalFile());

        UUID uid = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        if(Version.getVersionInstance().isAtLeast(Version.v1_20_R3)){
            p.addResourcePack(uid, url, digest, "§a服务器材质包: " + packName, forceEnableTextureLoad);
        }else {
            p.setResourcePack(uid, url, digest, "§a服务器材质包: " + packName, forceEnableTextureLoad);
        }
        return true;
    }

    public void loadAllResourcePack(Player p){
        for (var entry : urls.entrySet()) {
            if(entry.getValue().isAutoEnable()){
                loadResourcePack(p, entry.getKey());
                if(!Version.getVersionInstance().isAtLeast(Version.v1_20_R3)){
                    break;
                }
            }

        }
    }

    public boolean unloadResourcePack(Player p, String packName){
        if(Version.getVersionInstance().isAtLeast(Version.v1_20_R3)){
            var urlE = urls.get(packName);
            if(urlE != null){
                p.removeResourcePacks(UUID.nameUUIDFromBytes(urlE.getUrl().getBytes(StandardCharsets.UTF_8)));
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean unloadAllResourcePacks(Player pl){

        if(Version.getVersionInstance().isAtLeast(Version.v1_20_R3)){
            pl.removeResourcePacks(urls.values().stream().map(url ->UUID.nameUUIDFromBytes(url.getUrl().getBytes(StandardCharsets.UTF_8)) ).toList());
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent player) {
        if(enableTextureLoad) {
            Player p = player.getPlayer();
            loadAllResourcePack(p);
        }
    }





    private void loadConfig(){
        enableTextureLoad = plugin.getPluginConfig().getResourceService().isEnableServerResourcePack();
        forceEnableTextureLoad = plugin.getPluginConfig().getResourceService().isForceEnablePack();
        urls.clear();
        urls.putAll(plugin.getPluginConfig().getResourceService().getResourcePackUrl());
        hashes.clear();
        for (var urlE : urls.values()){
            if(urlE.getLocalFile() != null && !urlE.getLocalFile().trim().isEmpty()){
                String filePath = "plugins/" + plugin.getName().replace(" ", "_") + "/" + urlE.getLocalFile();
                File file = new File(filePath);
                if(file.exists() && file.isFile()){
                    byte[] bytes = FileUtils.readBinaryFile(file);
                    byte[] hashResult = Hashing.sha1().hashBytes(bytes).asBytes();
                    hashes.put(urlE.getLocalFile(), hashResult);
                }
            }
        }
        serviceConfig = plugin.getPluginConfig().getResourceService().getService();
    }

    private void registerFunctional(){
        Preconditions.checkArgument(!register, "Already Initialized");
        register = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private void unregisterFunctional(){
        Preconditions.checkArgument(register, "Not Unregistered");
        register = false;
        HandlerList.unregisterAll(this);
    }

    private void loadResourcePacks(){
        if(enableTextureLoad){
            for (var pl : Bukkit.getOnlinePlayers()){
                loadAllResourcePack(pl);
            }
        }

    }
    private void removeResourcePacks(){
        if(Version.getVersionInstance().isAtLeast(Version.v1_20_R3)){
            //only after 1.20.4 can we remove player resourcepacks
            if(enableTextureLoad) {
                for(var pl : Bukkit.getOnlinePlayers()){
                    unloadAllResourcePacks(pl);
                }
            }
        }
    }

    private void trySetupServer(){
        if(serviceConfig.isEnable()){
            httpServer = new Service(plugin, serviceConfig);
        }
    }

    private void tryStopServer(){
        if(httpServer != null){
            httpServer.shutdown();
            httpServer = null;
        }
    }


    @Override
    public <T extends Manager> T init(Plugin pl, String... path) {
        this.plugin = (AdvanceTexture) pl;
        addToRegistry();
        loadConfig();
        registerFunctional();
        loadResourcePacks();
        trySetupServer();
        return (T) this;
    }

    @Override
    public <T extends Manager> T reload() {
        deconstruct();
        return init(plugin);
    }

    @Override
    public void deconstruct() {
        tryStopServer();
        removeResourcePacks();
        unregisterFunctional();
        removeFromRegistry();
    }


    public static class Service implements HttpHandler {
        private boolean enabled;
        private HttpServer server;
        private final ServerPackageServiceConfig config;
        private final Set<String> validPath;
        private final Plugin plugin;
        private final ExecutorService executor;
        private int rateLimit = 0;
        private long lastMs = 0;
        public Service(Plugin pl,  ServerPackageServiceConfig config){
            Preconditions.checkArgument(config != null && config.isEnable(), "Config cannot be null or disabled");
            this.plugin = pl;
            this.config = config;
            this.executor = new ThreadPoolExecutor(1, 10, 30, TimeUnit.SECONDS,new SynchronousQueue<>());
            this.validPath = new LinkedHashSet<>(config.getResourcePackPath());
            setupHttpServer();
        }
        private final Map<String, FileCache> resourcePackCache = new ConcurrentHashMap<>();
        private BukkitTask cleanTask;
        protected void setupHttpServer(){
            try {
                server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
                server.setExecutor(executor);

                // 注册处理器
                server.createContext("/", this);

                server.start();
                Debug.logger("Resource pack download server started on port", config.getPort());

                // 注册关闭钩子
                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
                //十分钟 10 * 20 * 60 游戏刻清理一次缓存
                this.cleanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::handleCleanCache, 10 * 20 * 60, 10 * 20 * 60);
                enabled = true;
            } catch (IOException e) {
                Debug.logger(e,"Failed to start resource download server");
                if(server != null){
                    server.stop(0);
                }
                enabled = false;
            }
        }
        protected void shutdown(){

            try{
                resourcePackCache.clear();;
                if(server != null){
                    server.stop(0);
                }
                Debug.logger("Resource pack download server stopped");
                executor.shutdown();
                if(cleanTask != null){
                    cleanTask.cancel();
                }
            }finally {
                enabled = false;
            }

        }

        private boolean isRateLimited() {
            if(lastMs + 1000 < System.currentTimeMillis() ){
                lastMs = System.currentTimeMillis();
                rateLimit = 0;
                return false;
                //一秒超过20次就爆
            }else return  ++rateLimit > 20;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try{
                if(isRateLimited()){
                    sendErrorResponse(exchange, 429, "Too many requests in one second");
                    return;
                }
                if(!"GET".equalsIgnoreCase(exchange.getRequestMethod())){
                    sendErrorResponse(exchange, 405, "Method not allowed");
                    return;
                }
                Headers sessions = exchange.getRequestHeaders();
                if(!validateHeader(sessions)){
                    sendErrorResponse(exchange, 400, "Invalid request header, not from mc client");
                    return;
                }
                String requestPath = exchange.getRequestURI().getPath();
                String fileName = requestPath.substring(1);
                if(fileName.isEmpty() || !validPath.contains(fileName)){
                    sendErrorResponse(exchange, 400, "Invalid file path");
                    return;
                }
                handleDownload(exchange, fileName);
            }catch (Throwable e){
                Debug.logger(e, "Error while handling request");
                sendErrorResponse(exchange, 500, "Internal server error");
            }


        }

        private void sendErrorResponse(HttpExchange exchange, int code, String message) throws IOException {
            String response = String.format("{\"error\":\"%s\",\"code\":%d}", message, code);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
        private final long fileCacheLiveTime = 30 * 1000;
        private void handleDownload(HttpExchange exchange, String path)  throws IOException {
            if(!resourcePackCache.containsKey(path)){
                String filePath = "plugins/" + plugin.getName().replace(" ", "_") + "/" + path;
                File file = new File(filePath);
                if(!file.exists()){
                    sendErrorResponse(exchange, 404, "File not found");
                    return;
                }
                byte[] bytes = FileUtils.readBinaryFile(filePath);
                resourcePackCache.put(path, new FileCache(bytes, System.currentTimeMillis()));
            }
            FileCache cache = resourcePackCache.get(path);
            if(cache == null){
                sendErrorResponse(exchange, 404, "File not found");
                return;
            }
            cache.lastVisited = System.currentTimeMillis();
            // 设置响应头
            Headers headers = exchange.getResponseHeaders();

            // 根据文件扩展名设置Content-Type
            String contentType = getContentType(path);
            headers.set("Content-Type", contentType);
//            headers.set("Content-Length", String.valueOf(cache.content.length));
            // 设置下载头
            headers.set("Content-Disposition", "attachment; filename=\"" + path + "\"");

            exchange.sendResponseHeaders(200, cache.content.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(cache.content);
            }

        }
        private String getContentType(String fileName) {
            if (fileName.endsWith(".zip")) {
                return "application/zip";
            } else if (fileName.endsWith(".jar")) {
                return "application/java-archive";
            } else if (fileName.endsWith(".txt")) {
                return "text/plain";
            } else if (fileName.endsWith(".json")) {
                return "application/json";
            } else if (fileName.endsWith(".png")) {
                return "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else {
                return "application/octet-stream";
            }
        }
        private void handleCleanCache(){
            //validPath是不会动了 这并不会并发
            for (var path : validPath){
                var re = resourcePackCache.get(path);
                //cache reach its death
                if(re != null && re.lastVisited + fileCacheLiveTime < System.currentTimeMillis()){
                    resourcePackCache.remove(path);
                }
            }
        }
        private final List<String> headersCheck = List.of(
            "X-minecraft-username",
            "X-Minecraft-UUID",
            "X-Minecraft-Pack-Format"
        );
        private boolean validateHeader(Headers headers){
            if(!config.isHeaderCheck())return true;
            for(String header : headersCheck){
                if(!headers.containsKey(header)){
                    return false;
                }
            }
            return true;
        }
        @AllArgsConstructor
        public static class FileCache{
            byte[] content;
            long lastVisited;
        }
    }
}
