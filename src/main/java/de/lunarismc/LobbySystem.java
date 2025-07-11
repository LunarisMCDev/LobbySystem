package de.lunarismc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LobbySystem extends JavaPlugin implements Listener {

    private Inventory navigatorInventory;
    private final Map<Integer, String> navigatorCommands = new HashMap<>();
    private final Map<Integer, ItemStack> joinItems = new HashMap<>();
    private final Map<String, String> itemCommands = new HashMap<>();
    private List<String> autoMessages = new ArrayList<>();
    private int autoInterval = 180;
    private BukkitRunnable messageTask;
    private List<String> randomModes = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigData();
        getServer().getPluginManager().registerEvents(this, this);
        startAutoMessages();
        getLogger().info("LobbySystem enabled");
    }

    @Override
    public void onDisable() {
        if (messageTask != null) messageTask.cancel();
        getLogger().info("LobbySystem disabled");
    }

    // -------------------------------- Configuration --------------------------------

    private void loadConfigData() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        autoMessages = cfg.getStringList("broadcast.messages");
        autoInterval = cfg.getInt("broadcast.interval", 180);

        loadNavigator(cfg.getConfigurationSection("navigator"));
        loadJoinItems(cfg.getConfigurationSection("join.items"));

        randomModes = cfg.getStringList("random_modes");
        if (randomModes.isEmpty()) {
            randomModes = Arrays.asList("Citybuild", "Survival", "Minigames");
        }
    }

    private void loadNavigator(ConfigurationSection section) {
        if (section == null) return;
        int size = section.getInt("size", 9);
        String title = ChatColor.translateAlternateColorCodes('&', section.getString("title", "Navigator"));
        navigatorInventory = Bukkit.createInventory(null, size, title);
        navigatorCommands.clear();
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection sec = items.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", 0);
                Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
                if (mat == null) continue;
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null && sec.isString("name")) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', sec.getString("name")));
                    item.setItemMeta(meta);
                }
                navigatorInventory.setItem(slot, item);
                if (sec.isString("command")) {
                    navigatorCommands.put(slot, sec.getString("command"));
                }
            }
        }
    }

    private void loadJoinItems(ConfigurationSection section) {
        joinItems.clear();
        itemCommands.clear();
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec == null) continue;
            int slot = sec.getInt("slot", 0);
            Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
            if (mat == null) continue;
            String name = ChatColor.translateAlternateColorCodes('&', sec.getString("name", key));
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                item.setItemMeta(meta);
            }
            joinItems.put(slot, item);
            if (sec.isString("command")) {
                itemCommands.put(name, sec.getString("command"));
            }
        }
    }

    private void startAutoMessages() {
        if (messageTask != null) messageTask.cancel();
        if (autoMessages.isEmpty()) return;
        messageTask = new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                String msg = ChatColor.translateAlternateColorCodes('&', autoMessages.get(index));
                Bukkit.broadcastMessage(msg);
                index = (index + 1) % autoMessages.size();
            }
        };
        messageTask.runTaskTimer(this, 0L, autoInterval * 20L);
    }

    // -------------------------------- Commands --------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lobbyreload")) {
            if (!sender.hasPermission("lobby.admin")) {
                sender.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                return true;
            }
            loadConfigData();
            startAutoMessages();
            sender.sendMessage(ChatColor.GREEN + "Lobby wurde erfolgreich neu geladen.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        switch (command.getName().toLowerCase()) {
            case "navigator":
                player.openInventory(navigatorInventory);
                break;
            case "profil":
                player.sendMessage(ChatColor.GREEN + "Profil geöffnet.");
                break;
            case "neuigkeiten":
                player.sendMessage(ChatColor.GREEN + "Neuigkeiten geöffnet.");
                break;
            case "kosmetik":
                player.sendMessage(ChatColor.GREEN + "Kosmetik-Menü geöffnet.");
                break;
            case "zufall":
                if (!randomModes.isEmpty()) {
                    String picked = randomModes.get(new Random().nextInt(randomModes.size()));
                    player.sendMessage(ChatColor.GREEN + "Zufällig verbunden mit " + ChatColor.YELLOW + picked + ChatColor.GREEN + "!");
                }
                break;
            default:
                return false;
        }
        return true;
    }

    // -------------------------------- Events --------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.setJoinMessage(null);
        p.teleport(p.getWorld().getSpawnLocation());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().clear();

        String title = ChatColor.translateAlternateColorCodes('&', getConfig().getString("join.title", ""));
        String subtitle = ChatColor.translateAlternateColorCodes('&', getConfig().getString("join.subtitle", ""));
        p.sendTitle(title, subtitle, 10, 60, 10);
        Sound s;
        try {
            s = Sound.valueOf(getConfig().getString("join.sound", "ENTITY_PLAYER_LEVELUP"));
        } catch (IllegalArgumentException ex) {
            s = Sound.ENTITY_PLAYER_LEVELUP;
        }
        p.playSound(p.getLocation(), s, 1f, 1f);
        setLobbyItems(p);
    }

    private void setLobbyItems(Player p) {
        for (Map.Entry<Integer, ItemStack> entry : joinItems.entrySet()) {
            p.getInventory().setItem(entry.getKey(), entry.getValue());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null) return;
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String name = meta.getDisplayName();
        String cmd = itemCommands.get(name);
        if (cmd != null) {
            e.setCancelled(true);
            e.getPlayer().performCommand(cmd);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().equals(navigatorInventory)) {
            event.setCancelled(true);
            String command = navigatorCommands.get(event.getSlot());
            if (command != null && event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.performCommand(command);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!e.getPlayer().hasPermission("lobby.build")) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!e.getPlayer().hasPermission("lobby.build")) e.setCancelled(true);
    }
}
