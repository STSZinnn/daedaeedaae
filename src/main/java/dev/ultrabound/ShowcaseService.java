package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;

import java.util.*;

/** Playable v0.2.0 showcase: authenticated engineer wrenches, one real healing
 * machine prototype, and temporary Beast Tamer companion. All entities are
 * removed on disable. This is NOT the finalized persistent machine system. */
public final class ShowcaseService implements Listener {
    private final UltraboundPlugin plugin;
    private final NamespacedKey stationOwner;
    private final Map<UUID, BlockDisplay> stations = new HashMap<>();
    private final Map<UUID, BukkitTask> stationPulseTasks = new HashMap<>();
    private final Map<UUID, Wolf> companions = new HashMap<>();
    private final Map<UUID, BukkitTask> companionExpiry = new HashMap<>();

    ShowcaseService(UltraboundPlugin plugin) {
        this.plugin=plugin;
        stationOwner=new NamespacedKey(plugin,"station_owner");
        // Clear any old tagged preview displays loaded after a crash/restart.
        for(World world:Bukkit.getWorlds())for(BlockDisplay display:world.getEntitiesByClass(BlockDisplay.class)){
            if(display.getPersistentDataContainer().has(stationOwner,PersistentDataType.STRING))display.remove();
        }
        registerWrenchRecipes();
    }
    private void registerWrenchRecipes() {
        Map<String,Material> materials = new LinkedHashMap<>();
        materials.put("stone", Material.COBBLESTONE);
        materials.put("iron", Material.IRON_INGOT);
        materials.put("diamond", Material.DIAMOND);
        materials.put("netherite", Material.NETHERITE_INGOT);
        for(var entry:materials.entrySet()) {
            String id=entry.getKey();
            NamespacedKey key=new NamespacedKey(plugin,"wrench_"+id);
            ShapedRecipe recipe=new ShapedRecipe(key,plugin.items().make("wrench_"+id,null));
            recipe.shape("M  "," M ","  S");
            recipe.setIngredient('M',entry.getValue());
            recipe.setIngredient('S',Material.STICK);
            Bukkit.addRecipe(recipe);
        }
    }
    private boolean engineer(Player player){return "ENGINEER".equals(plugin.players().get(player.getUniqueId()).skillset);}
    private String wrenchTier(ItemStack stack){
        String id=plugin.items().identity(stack);
        return id!=null && id.startsWith("wrench_")?id.substring(7):null;
    }
    private int tierLevel(String tier){return switch(tier){case "stone"->1;case "iron"->2;case "diamond"->3;case "netherite"->4;default->0;};}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void craft(CraftItemEvent e){
        if(!(e.getWhoClicked() instanceof Player player))return;
        if(wrenchTier(e.getRecipe().getResult())!=null && !engineer(player)){
            e.setCancelled(true);
            player.sendMessage(ChatColor.RED+"Only Engineers can craft Ultrabound Wrenches.");
        }
    }

    public void wrenchUse(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND || !e.getAction().isRightClick())return;
        Player player=e.getPlayer();String tier=wrenchTier(player.getInventory().getItemInMainHand());
        if(tier==null)return;
        e.setCancelled(true);
        if(!engineer(player)){player.sendMessage(ChatColor.RED+"Only Engineers can use Wrenches.");return;}
        openRoot(player,tier);
    }
    private static final class MenuHolder implements InventoryHolder {
        private final String screen,tier;
        private Inventory inv;
        MenuHolder(String screen,String tier){this.screen=screen;this.tier=tier;}
        public Inventory getInventory(){return inv;}
    }
    private Inventory newMenu(String title,MenuHolder h){
        h.inv=Bukkit.createInventory(h,27,ChatColor.DARK_AQUA+"Engineer » "+title);
        return h.inv;
    }
    private ItemStack icon(Material type,String name,String... notes){
        ItemStack stack=new ItemStack(type);
        ItemMeta meta=stack.getItemMeta();meta.setDisplayName(name);
        meta.setLore(Arrays.stream(notes).map(s->ChatColor.GRAY+s).toList());
        stack.setItemMeta(meta);return stack;
    }
    private void openRoot(Player player,String tier){
        MenuHolder holder=new MenuHolder("root",tier);
        Inventory inv=newMenu("Wrench",holder);
        inv.setItem(11,icon(Material.CROSSBOW,ChatColor.RED+"WEAPONS","Ballistic / Explosive / Advanced","Design previews; not yet operational"));
        inv.setItem(15,icon(Material.GHAST_TEAR,ChatColor.AQUA+"HEALING","Medic prototype and future machines"));
        player.openInventory(inv);
    }
    private void openHealing(Player player,String tier){
        MenuHolder holder=new MenuHolder("healing",tier);
        Inventory inv=newMenu("Healing",holder);
        boolean unlocked=plugin.players().rank(plugin.players().get(player.getUniqueId()),"medic-turret-unlock")>0;
        inv.setItem(11,icon(unlocked?Material.BEACON:Material.BARRIER,
            unlocked?ChatColor.GREEN+"Deploy Medic Turret":ChatColor.RED+"Medic Turret (LOCKED)",
            "Requires Maintenance & Restoration branch","and Medic Turret Unlock node.",
            "Prototype: one 45s healing emitter; infinite supply."));
        inv.setItem(13,icon(Material.ENCHANTING_TABLE,ChatColor.YELLOW+"Experience Infuser","Design preview, not available"));
        inv.setItem(15,icon(Material.REDSTONE_LAMP,ChatColor.YELLOW+"Other Medical Machines","Design preview, not available"));
        inv.setItem(22,icon(Material.ARROW,ChatColor.WHITE+"Back"));
        player.openInventory(inv);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void guiClick(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player player))return;
        int slot=e.getRawSlot();if(slot<0 || slot>=27)return;
        if(!engineer(player)){player.closeInventory();return;}
        // Validate actual in-hand Wrench at EVERY action; GUI alone never authorizes deployment.
        String held=wrenchTier(player.getInventory().getItemInMainHand());
        if(held==null || tierLevel(held)<tierLevel(h.tier)){
            player.closeInventory();player.sendMessage(ChatColor.RED+"Keep your Wrench equipped.");return;
        }
        if(h.screen.equals("root") && slot==15){openHealing(player,held);return;}
        if(h.screen.equals("root") && slot==11){player.sendMessage(ChatColor.YELLOW+"Offensive machines are design previews in this build.");return;}
        if(h.screen.equals("healing") && slot==22){openRoot(player,held);return;}
        if(h.screen.equals("healing") && slot==11){
            if(plugin.players().rank(plugin.players().get(player.getUniqueId()),"medic-turret-unlock")<1){
                player.sendMessage(ChatColor.RED+"Unlock the Medic Turret node in your skill tree first.");return;
            }
            player.closeInventory();
            deployStation(player);
        }
    }
    @EventHandler public void guiDrag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof MenuHolder)e.setCancelled(true);
    }
    private void deployStation(Player player){
        if(!plugin.getConfig().getBoolean("engineer.medic-preview.enabled",true)){
            player.sendMessage(ChatColor.RED+"Medic prototype disabled by the server owner.");return;
        }
        if(plugin.getConfig().getStringList("ability.disabled-worlds").contains(player.getWorld().getName()))return;
        UUID owner=player.getUniqueId();
        if(stations.containsKey(owner) && stations.get(owner).isValid()){
            player.sendMessage(ChatColor.YELLOW+"Retrieve your current Medic Turret first (expires automatically).");return;
        }
        Location loc=player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        loc.setY(player.getLocation().getY());
        // Entity-only prototype: never changes terrain or forces placement inside claims.
        if(!loc.getBlock().isPassable() || loc.getBlock().getRelative(0,-1,0).isPassable()){
            player.sendMessage(ChatColor.RED+"Deploy on open ground in front of you.");return;
        }
        BlockDisplay display=player.getWorld().spawn(loc,BlockDisplay.class);
        display.setBlock(Bukkit.createBlockData(Material.CYAN_STAINED_GLASS));
        display.getPersistentDataContainer().set(stationOwner,PersistentDataType.STRING,owner.toString());
        stations.put(owner,display);
        player.getWorld().spawnParticle(Particle.END_ROD,loc,18,.5,.5,.5,.03);
        player.playSound(loc,Sound.BLOCK_BEACON_ACTIVATE,.6f,1.3f);
        int duration=Math.max(5,Math.min(120,plugin.getConfig().getInt("engineer.medic-preview.lifetime-seconds",45)));
        int interval=Math.max(20,Math.min(200,plugin.getConfig().getInt("engineer.medic-preview.pulse-ticks",40)));
        BukkitTask pulses=Bukkit.getScheduler().runTaskTimer(plugin,()->pulseStation(owner),interval,interval);
        stationPulseTasks.put(owner,pulses);
        Bukkit.getScheduler().runTaskLater(plugin,()->removeStation(owner),duration*20L);
        player.sendMessage(ChatColor.AQUA+"Medic Turret deployed for "+duration+"s (preview).");
    }
    private void pulseStation(UUID owner){
        BlockDisplay display=stations.get(owner);Player player=Bukkit.getPlayer(owner);
        if(display==null || !display.isValid() || player==null || !engineer(player)){
            removeStation(owner);return;
        }
        double range=Math.max(2,Math.min(16,plugin.getConfig().getDouble("engineer.medic-preview.range",6)));
        double amount=Math.max(.1,Math.min(8,plugin.getConfig().getDouble("engineer.medic-preview.heal-per-pulse",1)));
        for(Player target:display.getWorld().getNearbyPlayers(display.getLocation(),range)){
            if(!target.getUniqueId().equals(owner) && !plugin.players().isTrusted(owner,target.getUniqueId()))continue;
            var attr=target.getAttribute(Attribute.MAX_HEALTH);
            double max=attr==null?20:attr.getValue();
            target.setHealth(Math.min(max,target.getHealth()+amount));
            target.getWorld().spawnParticle(Particle.HEART,target.getLocation().clone().add(0,1.5,0),2,.3,.2,.3,0);
        }
        display.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,display.getLocation().clone().add(.5,.8,.5),6,.3,.2,.3,0);
    }
    public void removeStation(UUID owner){
        BukkitTask task=stationPulseTasks.remove(owner);if(task!=null)task.cancel();
        BlockDisplay display=stations.remove(owner);if(display!=null && display.isValid())display.remove();
    }
    public boolean callCompanion(Player player,int seconds){
        if(!"BEAST_TAMER".equals(plugin.players().get(player.getUniqueId()).skillset))return false;
        UUID owner=player.getUniqueId();
        if(companions.containsKey(owner) && companions.get(owner).isValid())return false;
        Wolf wolf=player.getWorld().spawn(player.getLocation().clone().add(1,0,1),Wolf.class);
        wolf.setTamed(true);wolf.setOwner(player);wolf.setCustomName(ChatColor.AQUA+player.getName()+"'s Companion");
        PlayerStore.Data data=plugin.players().get(owner);
        int bond=plugin.players().rank(data,"deep-bond");
        if(bond>0){var health=wolf.getAttribute(Attribute.MAX_HEALTH);
            if(health!=null){health.setBaseValue(health.getBaseValue()+Math.min(12,bond*2));wolf.setHealth(health.getValue());}}
        int loyalty=plugin.players().rank(data,"loyal-companion");
        if(loyalty>0)wolf.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,Math.max(5,seconds)*20,0,true,false,true));
        wolf.setCustomNameVisible(true);
        companions.put(owner,wolf);
        BukkitTask task=Bukkit.getScheduler().runTaskLater(plugin,()->removeCompanion(owner),Math.max(5,seconds)*20L);
        companionExpiry.put(owner,task);
        return true;
    }
    public void removeCompanion(UUID owner){
        BukkitTask task=companionExpiry.remove(owner);if(task!=null)task.cancel();
        Wolf wolf=companions.remove(owner);if(wolf!=null && wolf.isValid())wolf.remove();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void safeTarget(EntityTargetLivingEntityEvent e){
        if(!(e.getEntity() instanceof Wolf wolf) || !(e.getTarget() instanceof Player victim))return;
        for(var entry:companions.entrySet()) if(entry.getValue().getUniqueId().equals(wolf.getUniqueId())){
            UUID owner=entry.getKey();
            if(owner.equals(victim.getUniqueId()) || plugin.players().isTrusted(owner,victim.getUniqueId()))e.setCancelled(true);
            return;
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void safeDamage(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Wolf wolf) || !(e.getEntity() instanceof Player target))return;
        for(var entry:companions.entrySet()) if(entry.getValue().getUniqueId().equals(wolf.getUniqueId())){
            if(!target.getWorld().getPVP() || entry.getKey().equals(target.getUniqueId()) || plugin.players().isTrusted(entry.getKey(),target.getUniqueId()))e.setCancelled(true);
            return;
        }
    }
    public void cleanupOwner(UUID owner){removeCompanion(owner);removeStation(owner);}
    public void shutdown(){
        for(UUID id:new HashSet<>(companions.keySet()))removeCompanion(id);
        for(UUID id:new HashSet<>(stations.keySet()))removeStation(id);
        // Remove orphan previews after an unexpected previous shutdown.
        for(World world:Bukkit.getWorlds())for(BlockDisplay display:world.getEntitiesByClass(BlockDisplay.class)){
            if(display.getPersistentDataContainer().has(stationOwner,PersistentDataType.STRING))display.remove();
        }
    }
}
