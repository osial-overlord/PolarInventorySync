package org.polar.polarinventorysync.data;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.polar.polarinventorysync.PolarInventorySync;
import org.polar.polarinventorysync.util.NBTEditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataHandler {

    private PolarInventorySync plugin;
    private int port;
    private String host;
    private String database;
    private String collection;
    private String username;
    private String password;

    private MongoClient client;
    private MongoDatabase databaseInstance;
    private MongoCollection<Document> collectionInstance;

    public DataHandler(PolarInventorySync plugin) {
        //Save Defaults
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();

        //Log
        this.plugin.getLogger().info("Connecting to database...");

        //Load Database Details
        this.port = plugin.getConfig().getInt("mongodb.port");
        this.host = plugin.getConfig().getString("mongodb.host");
        this.database = plugin.getConfig().getString("mongodb.database");
        this.collection = plugin.getConfig().getString("mongodb.collection");
        this.username = plugin.getConfig().getString("mongodb.username");
        this.password = plugin.getConfig().getString("mongodb.password");

        //Connect to the Database
        this.client = new MongoClient(host, port);
        this.databaseInstance = this.client.getDatabase(this.database);
        this.collectionInstance = this.databaseInstance.getCollection(this.collection);

        //Log
        this.plugin.getLogger().info("Successfully connected to the MongoDB database.");
    }

    public void savePlayerInventory(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack[] items = inventory.getContents();
        Document inventoryDocument = new Document();
        inventoryDocument.append("player", player.getName());
        inventoryDocument.append("uuid", player.getUniqueId().toString());
        boolean isEmpty = true;
        for (ItemStack item : items) {
            if (item != null) {
                int slot = inventory.first(item);
                Document serialized = serializeItemStack(item);
                inventoryDocument.append("slot" + slot, serialized);
                isEmpty = false;
            }
        }

        //If Inventory is Empty, Add AIR to all existing slots.
        if (isEmpty) {
            for (int i = 0; i < 36; i++) {
                inventoryDocument.append("slot" + i, new Document("material", "AIR"));
            }
        }

        /*
        Save document to the database with the following conditions.
        1.) If document already exists with the same username or uuid, overwrite it.
        2.) If the document does not exist, create it.
         */
        Document query = new Document();
        query.append("player", player.getName());
        query.append("uuid", player.getUniqueId().toString());
        Document existingDocument = this.collectionInstance.find(query).first();
        if (existingDocument != null) {
            this.collectionInstance.deleteOne(query);
            this.collectionInstance.insertOne(inventoryDocument);
        } else {
            this.collectionInstance.insertOne(inventoryDocument);
        }
    }

    private Document serializeItemStack(ItemStack i) {
        /*
        Serialize an item in a MongoDB Document
         */
        String material = i.getType().toString();
        int amount = i.getAmount();
        short damage = i.getDurability();
        String displayName = i.getItemMeta().getDisplayName();
        List<String> lore = i.getItemMeta().getLore();
        Map<String, Integer> enchantments = new HashMap<>();
        for (Enchantment enchantment : i.getEnchantments().keySet()) {
            enchantments.put(enchantment.getName(), i.getEnchantmentLevel(enchantment));
        }

        List<Material> colorable = new ArrayList<>();
        colorable.add(Material.LEATHER_HELMET);
        colorable.add(Material.LEATHER_CHESTPLATE);
        colorable.add(Material.LEATHER_LEGGINGS);
        colorable.add(Material.LEATHER_BOOTS);

        Document item = new Document("material", material)
                .append("amount", amount)
                .append("damage", damage)
                .append("displayName", displayName)
                .append("lore", lore);

        //If material is in colorable, get the LeatherColor.
        if (colorable.contains(i.getType())) {
            LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
            int color = meta.getColor().asRGB();
            item.append("color", color);
        }

        String nbt = NBTEditor.getNBTCompound(i).toJson();
        item.append("nbt", nbt);

        if (enchantments.size() > 0) {
            item.append("enchantments", enchantments);
        }
        return item;
    }

    /*
    From a player's UUID, search for documents containing information on their
    inventory. If a document can be found, overwrite the player's current inventory
    with the list of items and slots found from the document.
     */
    public void loadInventoryContents(Player player) {
        Document query = new Document();
        query.append("uuid", player.getUniqueId().toString());
        Document existingDocument = this.collectionInstance.find(query).first();
        if (existingDocument != null) {
            Map<Integer, ItemStack> inventoryMap = deserializeContents(existingDocument, player);
            Inventory inventory = player.getInventory();
            for (Map.Entry<Integer, ItemStack> entry : inventoryMap.entrySet()) {
                inventory.setItem(entry.getKey(), entry.getValue());
            }
        }
    }

    private Map<Integer, ItemStack> deserializeContents(Document d, Player player) {
        /*
        Using the methods set out in savePlayerInventory() and serializeItemStack(),
        convert the document into a list of items using the deserializeItemStack() method.
         */
        Map<Integer, ItemStack> contents = new HashMap<>();
        for (String key : d.keySet()) {
            if (key.startsWith("slot")) {
                int slot = Integer.parseInt(key.substring(4));
                Document item = (Document) d.get(key);
                ItemStack itemStack = deserializeItemStack(item, player, slot);
                contents.put(slot, itemStack);
            }
        }
        return contents;
    }

    private ItemStack deserializeItemStack(Document d, Player player, int slot) {
        ItemStack item = new ItemStack(Material.getMaterial(d.getString("material")));
        if (d.getInteger("amount") != null) {
            item.setAmount(d.getInteger("amount"));
        }
        if (d.getInteger("damage") != null) {
            item.setDurability(d.getInteger("damage").shortValue());
        }
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(d.getString("displayName"));
        m.setLore(d.getList("lore", String.class));
        if (d.containsKey("color")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) m;
            meta.setColor(Color.fromRGB(d.getInteger("color")));
            item.setItemMeta(meta);
        }
        if (d.containsKey("nbt")) {
            System.out.println("RECEIVED: " + d.getString("nbt"));
            try {
                item = addTags(d.getString("nbt"), item, player, slot);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        item.setItemMeta(m);
        //If document contains enchantments, load a String, Integer map of enchants
        if (d.containsKey("enchantments")) {
            Map<String, Integer> enchantments = d.get("enchantments", Map.class);
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                System.out.println(entry.getKey() + " " + entry.getValue());
                Enchantment enchant = Enchantment.getByName(entry.getKey());
                if (enchant != null) {
                    item.addUnsafeEnchantment(enchant, entry.getValue());
                }
            }
        }
        return item;
    }

    private ItemStack addTags(String json, ItemStack item, Player player, int slot) throws ParseException {
        //For all keys in the json object, see if those objects contain a value, if they contain a value which is not another json object, set the items tag.
        //If the value is a json object, create a jsonobject with the unadded keys and recursively call this method.
        //Do this in Bson
        NBTEditor.NBTCompound nbt = NBTEditor.getNBTCompound(json);
        for (String key : NBTEditor.getKeys(nbt)) {
            if (item != null) {
                if (!NBTEditor.get(nbt, key).getClass().getSimpleName().equalsIgnoreCase("HashMap")) {
                    System.out.println(key + ": " + NBTEditor.get(nbt, key));
                    System.out.println(key + ": " + NBTEditor.get(nbt, key).getClass().getSimpleName());
                    item = NBTEditor.set(item, NBTEditor.get(nbt, key), key);
                } else {
                    HashMap<Object, Object> map = (HashMap<Object, Object>) NBTEditor.get(nbt, key);
                    for (Object k : map.keySet()) {
                        System.out.println(k + ": " + map.get(k));
                        item = NBTEditor.set(item, map.get(k), k);
                    }
                }
            }
        }
        player.getInventory().setItem(slot, item);

        return item;
    }

}
