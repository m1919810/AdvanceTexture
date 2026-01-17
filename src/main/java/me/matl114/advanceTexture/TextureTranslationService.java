package me.matl114.advanceTexture;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.recipe.data.MerchantOffer;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matl114.matlib.core.Manager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;

public class TextureTranslationService extends PacketListenerAbstract implements Manager {
    @Getter
    private static  TextureTranslationService manager;
    AdvanceTexture plugin;
    private boolean registered = false;
    private Map<String, String> namespacedMpa;
    private Map<String, Function<NBTCompound, String>> optionalIdExtractor = new LinkedHashMap<>();
    private String customCmdSavedKey;
    private String customImSaveKey;
    private boolean enableService = false;
    private boolean enableCmd = false;
    private boolean enableItemModel = false;
    public TextureTranslationService() {
        manager = this;
    }

    @Override
    public boolean isAutoDisable() {
        return true;
    }

    @Override
    public <T extends Manager> T init(Plugin pl, String... path) {
        this.plugin = (AdvanceTexture) pl;
        this.namespacedMpa = new LinkedHashMap<>(this.plugin.getPluginConfig().getTextureService().getCustomIdPaths());
        buildExtractors();
        enableCmd =  this.plugin.getPluginConfig().getTextureService().isEnableTexture();
        enableService = enableCmd || enableItemModel;
        customCmdSavedKey = new NamespacedKey(pl, "save_key_cmd").asString();
        addToRegistry();
        registerFunctional();
        return (T) this;
    }

    private void buildExtractors(){
        optionalIdExtractor.clear();
        for (var entry: namespacedMpa.entrySet()) {
            final String[] splits = entry.getValue().split("\\.");
            final int len = splits.length;
            final String namespace = entry.getKey();
            Function<NBTCompound, String> function = (nbt) ->{
                for (var i = 0; i < len - 1; i++){
                    if(nbt.getTagOrNull(splits[i]) instanceof NBTCompound nbt2){
                        nbt = nbt2;
                    }else{
                        return null;
                    }
                }
                var str = nbt.getTagOrNull(splits[len - 1]);
                return str instanceof NBTString strNbt? strNbt.getValue() : null;
            };
            optionalIdExtractor.put(namespace, function);
        }
    }

    private void registerFunctional(){
        Preconditions.checkState(!registered, "Already initialized");
        registered = true;
        this.plugin.getPacketEventsAPI().getEventManager().registerListener(this);
    }

    private void unregisterFunctional(){
        Preconditions.checkState(registered, "Not initialized");
        registered = false;
        this.plugin.getPacketEventsAPI().getEventManager().unregisterListener(this);
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

    public boolean isEnableTranslate(){
        return enableService && !optionalIdExtractor.isEmpty();
    }


    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);
        if(!isEnableTranslate()) return;
        var packetType = event.getPacketType();
        ItemStack result;
        if(packetType == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION){
            WrapperPlayClientCreativeInventoryAction action = new WrapperPlayClientCreativeInventoryAction(event);
            ItemStack stack = action.getItemStack();
            result = tryUnMockId(stack, event.getServerVersion());
            if(result != null){
                action.setItemStack(result);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Client.CLICK_WINDOW){
            WrapperPlayClientClickWindow action = new WrapperPlayClientClickWindow(event);
            ItemStack stack = action.getCarriedItemStack();
            result = tryUnMockId(stack, event.getServerVersion());
            if(result != null){
                action.setCarriedItemStack(result);
                event.markForReEncode(true);
            }
            var hashMap = action.getSlots();
            if(hashMap.isPresent()){
                Map<Integer, ItemStack> map0 = new HashMap<>(hashMap.get());
                boolean changed = false;
                for (var entry: map0.entrySet()){
                    result = tryUnMockId(entry.getValue(), event.getServerVersion());
                    if (result != null){
                        entry.setValue(result);
                        changed = true;
                    }
                }
                if(changed){
                    action.setSlots(map0);
                    event.markForReEncode(true);
                }
            }
        }
    }


    @Override
    public void onPacketSend(PacketSendEvent event) {
        super.onPacketSend(event);
        if (!isEnableTranslate())return;
        var packetType = event.getPacketType();
        ItemStack result ;
        if(packetType == PacketType.Play.Server.SET_CURSOR_ITEM){
            WrapperPlayServerSetCursorItem wrapper = new WrapperPlayServerSetCursorItem(event);
            ItemStack stack = wrapper.getStack();
            result = tryMockId(stack, event.getServerVersion());
            if(result != null){
                wrapper.setStack(result);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.SET_PLAYER_INVENTORY){
            WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(event);
            ItemStack stack = wrapper.getStack();
            result = tryMockId(stack, event.getServerVersion());
            if(result != null){
                wrapper.setStack(result);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.SET_SLOT){
            WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
            ItemStack stack = wrapper.getItem();
            result = tryMockId(stack, event.getServerVersion());
            if(result != null){
                wrapper.setItem(result);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.ENTITY_EQUIPMENT){
            WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
            boolean changed = false;
            var list = wrapper.getEquipment();
            for (var en : list){
                result = tryMockId(en.getItem(), event.getServerVersion());
                if(result != null){
                    en.setItem(result);
                    changed = true;
                }
            }
            if(changed){
                wrapper.setEquipment(list);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.WINDOW_ITEMS){
            WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
            boolean changed = false;
            List<ItemStack> list =  wrapper.getItems();
            for (int i = 0; i < list.size() ; ++i){
                var en = list.get(i);
                result = tryMockId(en, event.getServerVersion());
                if (result != null){
                    //copy on write
                    if(!changed){
                        list = new ArrayList<>(list);
                    }
                    list.set(i, result);
                    changed = true;
                }
            }
            if(wrapper.getCarriedItem().isPresent()){
                result = tryMockId(wrapper.getCarriedItem().get(), event.getServerVersion());
                if(result != null){
                    changed = true;
                    wrapper.setCarriedItem(result);
                }
            }
            if(changed){
                wrapper.setItems(list);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.ENTITY_METADATA){
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            List<EntityData<?>> list = wrapper.getEntityMetadata();
            boolean changed = false;
            for (int i = 0; i < list.size() ; ++i){
                var en = list.get(i);
                if(en.getType() == EntityDataTypes.ITEMSTACK && en.getValue() instanceof ItemStack it){
                    result = tryMockId(it, event.getServerVersion());
                    if(result != null){
                        if(!changed){
                            list = new ArrayList<>(list);
                        }
                        ((EntityData)en).setValue(result);
                        list.set(i, en);
                        changed = true;
                    }
                }
            }
            if (changed){
                wrapper.setEntityMetadata(list);
                event.markForReEncode(true);
            }
        }else if(packetType == PacketType.Play.Server.MERCHANT_OFFERS){
            WrapperPlayServerMerchantOffers wrapper = new WrapperPlayServerMerchantOffers(event);
            boolean changed = false;
            List<MerchantOffer> offers = wrapper.getMerchantOffers();
            for (int i = 0; i < offers.size() ; ++i){
                var en = offers.get(i);
                var it1 = en.getFirstInputItem();
                result = tryMockId(it1, event.getServerVersion());
                boolean changeEntry = false;
                if (result != null){
                    en.setFirstInputItem(result);
                    changeEntry = true;
                }
                var it2 = en.getSecondInputItem();
                result = tryMockId(it2, event.getServerVersion());
                if(result != null){
                    en.setSecondInputItem(result);
                    changeEntry = true;
                }
                if(changeEntry){
                    if(!changed){
                        offers = new ArrayList<>(offers);
                    }
                    offers.set(i, en);
                    changed = true;
                }
            }
            if(changed){
                wrapper.setMerchantOffers(offers);
                event.markForReEncode(true);
            }
        }
    }


    //private static final String PDC_PATH = "PublicBukkitValues";
    private static final String LEGACY_PATH_CMD = "CustomModelData";
    private String getCustomId(NBTCompound nbt){
        for (var entry: optionalIdExtractor.entrySet()){
            String optionalId = entry.getValue().apply(nbt);
            if(optionalId != null){
                return entry.getKey() + ":" + optionalId;
            }
        }
        return null;
    }



    private ItemStack tryMockId(ItemStack stack, ServerVersion version){
        if(stack == null)return null;
        ItemStack stackCopy = null;
        if(!version.isNewerThanOrEquals(ServerVersion.V_1_20_5) ){
            //version less than 1.20.5
            if(enableCmd){
                if(stack.getNBT() != null){
                    //nbt version
                    NBTCompound nbt = stack.getNBT();
                    String sfid = getCustomId(nbt);
                    if(sfid != null){
                        int cmd = TextureIdService.getManager().getTextureIdOr(sfid);
                        if(cmd > 0){
                            stackCopy = stack.copy();
                            nbt = stackCopy.getNBT();
                            if(nbt != null){
                                var tag = nbt.getTagOrNull(LEGACY_PATH_CMD);
                                nbt.setTag(customCmdSavedKey, tag == null ? new NBTCompound() : tag);
                                nbt.setTag(LEGACY_PATH_CMD, new NBTInt(cmd));
                            }

                        }
                    }
                }
            }
            return stackCopy;
        }else {
            var nbtUnsafe = stack.getComponents().getPatches().get(ComponentTypes.CUSTOM_DATA);
            if(nbtUnsafe != null && nbtUnsafe.isPresent() && nbtUnsafe.get() instanceof NBTCompound nbt){
                String string = getCustomId(nbt);
                if(string != null){
                    NBTCompound nbtCopy = null;
                    if(enableCmd){
                        int cmd = TextureIdService.getManager().getTextureIdOr(string);
                        if(cmd > 0){
                            if(stackCopy == null){
                                stackCopy = stack.copy();
                            }
                            if(nbtCopy  == null){
                                nbtCopy = nbt.copy();
                            }
                            var cmdCp = stackCopy.getComponents().getPatches().get(ComponentTypes.CUSTOM_MODEL_DATA_LISTS);
                            if(cmdCp != null){
                                if(cmdCp.isPresent() && cmdCp.get() instanceof ItemCustomModelData cmdCpp){
                                    nbtCopy.setTag(customCmdSavedKey, new NBTInt(cmdCpp.getLegacyId()));
                                }else{
                                    nbtCopy.setTag(customCmdSavedKey, new NBTByte((byte) 0));
                                }
                            }else{
                                nbtCopy.setTag(customCmdSavedKey, new NBTByte((byte) 1));
                            }
                            stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.of(nbtCopy));
                            stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_MODEL_DATA_LISTS, Optional.of(new ItemCustomModelData(cmd)));
                        }
                    }
                    if(enableItemModel){
                        String model = TextureIdService.getManager().getTextureModel(string);
                        if(model != null && !model.isEmpty()){
                            if(stackCopy == null){
                                stackCopy = stack.copy();
                            }
                            if(nbtCopy == null){
                                nbtCopy = nbt.copy();
                            }
                            var cmdCp = stackCopy.getComponents().getPatches().get(ComponentTypes.ITEM_MODEL);
                            if(cmdCp != null){
                                if(cmdCp.isPresent() && cmdCp.get() instanceof ItemModel cmdCpp){
                                    nbtCopy.setTag(customImSaveKey, new NBTString(cmdCpp.getModelLocation().toString()));
                                }else{
                                    nbtCopy.setTag(customImSaveKey, new NBTByte((byte) 0));
                                }
                            }else{
                                nbtCopy.setTag(customImSaveKey, new NBTByte((byte) 1));
                            }
                            stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.of(nbtCopy));
                            stackCopy.getComponents().getPatches().put(ComponentTypes.ITEM_MODEL, Optional.of(new ItemModel(new ResourceLocation(model))));
                        }
                    }


                }
            }
            return stackCopy;

        }
    }
    private ItemStack tryUnMockId(ItemStack stack, ServerVersion version){
        if(stack == null)return null;
        if(!version.isNewerThanOrEquals(ServerVersion.V_1_20_5) ){
            //version less than 1.20.5
            if(stack.getNBT() != null){
                //nbt version
                NBTCompound nbt = stack.getNBT();
                ItemStack stackCopy = null;
                NBTCompound nbtCopy = null;
                var saveKey = nbt.getTagOrNull(customCmdSavedKey);
                if(saveKey != null){
                    if(saveKey instanceof NBTCompound map0){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = stack.getNBT();
                        }
                        if(nbtCopy != null){
                            nbtCopy.removeTag(LEGACY_PATH_CMD);
                            nbtCopy.removeTag(customCmdSavedKey);

                        }
                        return stackCopy;
                    }
                    if (saveKey instanceof NBTInt int0){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = stack.getNBT();
                        }
                        if(nbtCopy != null){
                            nbtCopy.setTag(LEGACY_PATH_CMD, int0);
                            nbtCopy.removeTag(customCmdSavedKey);
                        }
                        return stackCopy;
                    }
                }
                return stackCopy;

            }
            return null;
        }else {
            var nbtUnsafe = stack.getComponents().getPatches().get(ComponentTypes.CUSTOM_DATA);
            if(nbtUnsafe != null && nbtUnsafe.isPresent() && nbtUnsafe.get() instanceof NBTCompound nbt){
                ItemStack stackCopy = null;
                NBTCompound nbtCopy = null;
                var saved = nbt.getTagOrNull(customCmdSavedKey);
                if(saved != null){
                    if(saved instanceof NBTByte flagByte){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = nbt.copy();
                        }
                        if(nbtCopy != null){
                            nbtCopy.removeTag(customCmdSavedKey);
                        }

                        stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.ofNullable(nbtCopy));
                        if(flagByte.getAsBool()){
                            stackCopy.getComponents().getPatches().remove(ComponentTypes.CUSTOM_MODEL_DATA_LISTS);
                        }else{
                            stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_MODEL_DATA_LISTS, Optional.empty());
                        }
                    }else if(saved instanceof NBTInt int0){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = nbt.copy();
                        }
                        nbtCopy.removeTag(customCmdSavedKey);
                        stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.ofNullable(nbtCopy));
                        stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_MODEL_DATA_LISTS, Optional.of(new ItemCustomModelData(int0.getAsInt())));
                    }
                }
                saved = nbt.getTagOrNull(customImSaveKey);
                if(saved != null){
                    if(saved instanceof NBTByte flagByte){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = nbt.copy();
                        }
                        if(nbtCopy != null){
                            nbtCopy.removeTag(customImSaveKey);
                        }

                        stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.ofNullable(nbtCopy));
                        if(flagByte.getAsBool()){
                            stackCopy.getComponents().getPatches().remove(ComponentTypes.ITEM_MODEL);
                        }else{
                            stackCopy.getComponents().getPatches().put(ComponentTypes.ITEM_MODEL, Optional.empty());
                        }
                    }else if(saved instanceof NBTString int0){
                        if(stackCopy == null){
                            stackCopy = stack.copy();
                            nbtCopy = nbt.copy();
                        }
                        if(nbtCopy != null){
                            nbtCopy.removeTag(customImSaveKey);
                        }
                        stackCopy.getComponents().getPatches().put(ComponentTypes.CUSTOM_DATA, Optional.ofNullable(nbtCopy));
                        stackCopy.getComponents().getPatches().put(ComponentTypes.ITEM_MODEL, Optional.of(new ItemModel(new ResourceLocation(int0.getValue()))));
                    }
                }
                return stackCopy;
            }
            return null;
        }
    }


}
