package me.matl114.advanceTexture;

import com.google.common.base.Preconditions;
import me.matl114.matlib.core.Manager;
import me.matl114.matlib.utils.ThreadUtils;
import me.matl114.matlib.utils.command.commandGroup.AbstractMainCommand;
import me.matl114.matlib.utils.command.commandGroup.CommandContext;
import me.matl114.matlib.utils.command.commandGroup.SubCommand;
import me.matl114.matlib.utils.command.commandGroup.TreeSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class AdvanceTextureMainCommand extends AbstractMainCommand implements Manager {
    private AdvanceTexture plugin;
    private boolean registered;
    private final String commandPermission = "advancetexture.command.main";
    private final String commandOpPermission = "advancetexture.command.op";
    private final String commandPackPermission = "advancetexture.command.pack";
    private final String commandName = "advancetexture";
    TreeSubCommand main ;
    {
        main = mainBuilder()
            .name(commandName)
            .permission(commandPermission)
            .build();
    }

    {
        main.subBuilder(SubCommand.taskBuilder())
            .name("reload")
            .helper("重载插件配置文件")
            .permission(commandOpPermission)
            .post(e -> e.executor(CommandContext.run(this::onReloadAsync)))
            .complete();
    }

    private void onReloadAsync(){
        ThreadUtils.executeSyncSched(this::onReload);
    }

    private void onReload(){
        if(plugin != null){
            plugin.reload();
        }
    }


    @Override
    public boolean isAutoDisable() {
        return true;
    }

    private void registerFunctional(){
        Preconditions.checkArgument(!registered, "Already initialized");
        registered = true;
        plugin.getServer().getPluginCommand(commandName).setExecutor(this);
        plugin.getServer().getPluginCommand(commandName).setTabCompleter(this);
    }

    private void unregisterFunctional(){
        Preconditions.checkArgument(registered, "Not initialized");
        registered = false;
        plugin.getServer().getPluginCommand(commandName).setExecutor(null);
        plugin.getServer().getPluginCommand(commandName).setTabCompleter(null);

    }

    {
        main.subBuilder(SubCommand.treeBuilder())
            .name("pack")
            .helper("资源包控制")
            .permission(commandPackPermission)
            .post(
                s -> s.subBuilder(
                        SubCommand.taskBuilder()
                    )
                    .name("add")
                    .helper("<pack_name> (<player_name>) 为玩家增加资源包")
                    .args(
                        b -> b.name("pack_name")
                            .tabSupplier(()-> ResourcePackService.getManager().getResourcePackNames().stream())
                    )
                    .args(
                        b -> b.name("player_name")
                            .tabSupplier(()-> Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    )
                    .permission(commandPackPermission)
                    .post(e -> e.executor(((var1, streamArgs, argsReader) -> {
                        String pack = streamArgs.nextNonnull();
                        String name = streamArgs.nextArg();
                        if(name != null && var1.hasPermission(commandPackPermission + ".other")){
                            Player p = Bukkit.getPlayer(name);
                            checkNonnull(p, "玩家不在线");
                            loadPack(p, pack);
                        }else{
                            loadPack(player(var1), pack);
                        }
                        return true;
                    })))
                    .complete()
                    .subBuilder(
                        SubCommand.taskBuilder()
                    )
                    .name("remove")
                    .helper("<pack_name> (<player_name>) 为玩家移除资源包")
                    .args(
                        b -> b.name("pack_name")
                            .tabSupplier(()-> ResourcePackService.getManager().getResourcePackNames().stream())
                    )
                    .args(
                        b -> b.name("player_name")
                            .tabSupplier(()-> Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    )
                    .permission(commandPackPermission)
                    .post(e -> e.executor(((var1, streamArgs, argsReader) -> {
                        String pack = streamArgs.nextNonnull();
                        String name = streamArgs.nextArg();
                        if(name != null && var1.hasPermission(commandPackPermission + ".other")){
                            Player p = Bukkit.getPlayer(name);
                            checkNonnull(p, "玩家不在线");
                            removePack(p, pack);
                        }else{
                            removePack(player(var1), pack);
                        }
                        return true;
                    })))
                    .complete()
                    .subBuilder(
                        SubCommand.taskBuilder()
                    )
                    .name("removeall")
                    .helper("(<player_name>) 为玩家移除全部资源包")
                    .args(
                        b -> b.name("player_name")
                            .tabSupplier(()-> Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    )
                    .permission(commandPackPermission)
                    .post(e -> e.executor(((var1, streamArgs, argsReader) -> {
                        String name = streamArgs.nextArg();
                        if(name != null && var1.hasPermission(commandPackPermission + ".other")){
                            Player p = Bukkit.getPlayer(name);
                            checkNonnull(p, "玩家不在线");
                            removeAllPack(p);
                        }else{
                            removeAllPack(player(var1));
                        }
                        return true;
                    })))
                    .complete()
            )
            .complete()

        ;
    }

    private void loadPack(Player sender, String str){
        if(ResourcePackService.getManager().getResourcePackNames().contains(str)){
            ThreadUtils.executeAsync(()-> {
                if(ResourcePackService.getManager().loadResourcePack(sender, str)){
                    sendMessage(sender, "&a成功加载");
                }else{
                    sendMessage(sender, "&c在加载途中发送意外");

                }
            }, 20);
        }else{
            sendMessage(sender, "&c不存在的资源包: " + str);
        }

    }

    private void removePack(Player sender, String str){
        if(ResourcePackService.getManager().getResourcePackNames().contains(str)){
            ThreadUtils.executeAsync(()-> {
                if(ResourcePackService.getManager().unloadResourcePack(sender, str)){
                    sendMessage(sender, "&a成功卸载");
                }else{
                    sendMessage(sender, "&c当前服务器版本并不支持卸载");
                }
            }, 20);
        }else{
            sendMessage(sender, "&c不存在的资源包: " + str);
        }
    }
    private void removeAllPack(Player sender){
        ThreadUtils.executeAsync(()-> {
            if(ResourcePackService.getManager().unloadAllResourcePacks(sender)){
                sendMessage(sender, "&a成功卸载");
            }else{
                sendMessage(sender, "&c当前服务器版本并不支持卸载");
            }
        }, 20);
    }



    @Override
    public <T extends Manager> T init(Plugin pl, String... path) {
        this.plugin = (AdvanceTexture) pl;
        addToRegistry();
        registerFunctional();
        return (T) this;
    }

    @Override
    public <T extends Manager> T reload() {
        deconstruct();
        return init(plugin);
    }

    @Override
    public void deconstruct() {
        unregisterFunctional();
        removeFromRegistry();
    }
}
