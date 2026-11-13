package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

/** Persistent, entity-only Engineer machines. Does not replace terrain or load chunks.
 * Experimental combat machines must be tested with the server's region protection plugin.
 */
public final class EngineerService implements Listener {
    private enum Type {
        MEDIC("medic-turret-unlock",1,"Medic Turret",Material.CYAN_STAINED_GLASS,40),
        REGEN("regeneration-turret-unlock",2,"Regeneration Turret",Material.LIME_STAINED_GLASS,80),
        BALLISTIC("ballistic-turret-unlock",1,"Ballistic Turret",Material.COPPER_BLOCK,40),
        EXPLOSIVE("explosive-turret-unlock",2,"Explosive Turret",Material.ORANGE_STAINED_GLASS,80),
        ADVANCED("advanced-turret-unlock",3,"Advanced Turret",Material.PURPLE_STAINED_GLASS,40),
        INFUSER("experience-infuser-unlock",2,"Experience Infuser",Material.ENCHANTING_TABLE,100);
        final String node, title;
        final int tier, ticks;
        final Material block;
        Type(String n,int t,String text,Material block,int ticks){node=n;tier=t;title=text;this.block=block;this.ticks=ticks;}
    }
    private record Machine(UUID id,UUID owner,UUID world,double x,double y,double z,Type type) {
        Location location(){ World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z); }
    }
    private static final class Holder implements InventoryHolder {
        final String screen;
        Inventory inv;
        Holder(String screen){this.screen=screen;}
        public Inventory getInventory(){return inv;}
    }
    private final UltraboundPlugin p;
    private final File file;
    private final NamespacedKey entityKey;
    private final Map<UUID,Machine> machines=new LinkedHashMap<>();
    private final Map<UUID,BlockDisplay> visuals=new HashMap<>();
    private int ticker=0;

    EngineerService(UltraboundPlugin p){
        this.p=p;file=new File(p.getDataFolder(),"engineer-machines.yml");
        entityKey=new NamespacedKey(p,"machine_identity");
        // Never allow a previous crash to leave duplicate visual markers.
        for(World world:Bukkit.getWorlds())for(BlockDisplay d:world.getEntitiesByClass(BlockDisplay.class))
            if(d.getPersistentDataContainer().has(entityKey,PersistentDataType.STRING))d.remove();
        load();
        Bukkit.getScheduler().runTaskTimer(p,this::tick,20L,20L);
    }
    private boolean isEngineer(Player user){return "ENGINEER".equals(p.players().get(user.getUniqueId()).skillset);}
    private String wrench(ItemStack stack){String id=p.items().identity(stack);return id!=null && id.startsWith("wrench_")?id.substring(7):null;}
    private int tier(String s){return switch(s==null?"":s){case "stone"->1;case "iron"->2;case "diamond"->3;case "netherite"->4;default->0;};}
    private boolean hasWrench(Player user){return isEngineer(user)&&tier(wrench(user.getInventory().getItemInMainHand()))>0;}
    private boolean hasNode(Player user,Type type){return p.players().rank(p.players().get(user.getUniqueId()),type.node)>0;}
    private ItemStack item(Material mat,String label,String...lore){ItemStack x=new ItemStack(mat);ItemMeta m=x.getItemMeta();m.setDisplayName(label);m.setLore(Arrays.stream(lore).map(s->ChatColor.GRAY+s).toList());x.setItemMeta(m);return x;}
    private Inventory menu(Holder h,String title){h.inv=Bukkit.createInventory(h,36,ChatColor.DARK_AQUA+"Engineer | "+title);for(int i=0;i<36;i++)if(i<9||i>=27)h.inv.setItem(i,item(Material.BLUE_STAINED_GLASS_PANE," "));return h.inv;}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void useWrench(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick())return;
        String held=wrench(e.getPlayer().getInventory().getItemInMainHand());if(held==null)return;
        e.setCancelled(true);
        if(!isEngineer(e.getPlayer())){e.getPlayer().sendMessage(ChatColor.RED+"Engineer class required.");return;}
        open(e.getPlayer(),"root");
    }
    private void open(Player user,String screen){
        if(!hasWrench(user))return;
        Holder h=new Holder(screen);Inventory inv=menu(h,screen.toUpperCase(Locale.ROOT));
        if(screen.equals("root")){
            inv.setItem(11,item(Material.CROSSBOW,ChatColor.RED+"Weapons","Ballistic and explosive turrets"));
            inv.setItem(15,item(Material.BEACON,ChatColor.AQUA+"Healing","Medic, regeneration and Infuser machines"));
            inv.setItem(22,item(Material.CHEST,ChatColor.GOLD+"Retrieval","Click to remove your nearest machine within 8 blocks."));
        }else{
            Type[] types=screen.equals("weapons")?new Type[]{Type.BALLISTIC,Type.EXPLOSIVE,Type.ADVANCED}:new Type[]{Type.MEDIC,Type.REGEN,Type.INFUSER};
            for(int i=0;i<types.length;i++){
                Type t=types[i];boolean unlocked=hasNode(user,t);boolean enough=tier(wrench(user.getInventory().getItemInMainHand()))>=t.tier;
                inv.setItem(11+i*2,item(unlocked&&enough?t.block:Material.BARRIER,
                    (unlocked&&enough?ChatColor.GREEN:ChatColor.RED)+t.title,
                    "Requires: "+t.node,"Minimum wrench tier: "+t.tier,
                    "Unlocked: "+unlocked,"Wrench sufficient: "+enough,
                    unlocked&&enough?"Click to deploy in front of you.":"Unlock through /ub skilltree first."));
            }
            if(screen.equals("healing")){
                PlayerStore.Data d=p.players().get(user.getUniqueId());
                inv.setItem(20,item(Material.EXPERIENCE_BOTTLE,ChatColor.GREEN+"Deposit 5 XP levels",
                    "Engineer reserve: "+d.storedXp+" XP units", "Consumes 5 of YOUR levels.","Exchange: "+p.getConfig().getInt("engineer.infuser.xp-per-deposit",150)+" reserve units."));
                inv.setItem(24,item(Material.ANVIL,ChatColor.YELLOW+"Repair Held Item",
                    "Requires a nearby owned or trusted Experience Infuser.","Shows repair cost first with /ub repair.","Then confirm using /ub repair confirm."));
            }
            inv.setItem(31,item(Material.ARROW,ChatColor.WHITE+"Back"));
        }
        user.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player user))return;
        int slot=e.getRawSlot();if(slot<0||slot>=36)return;
        if(!hasWrench(user)){user.closeInventory();return;}
        if(h.screen.equals("root")){
            if(slot==11)open(user,"weapons");
            else if(slot==15)open(user,"healing");
            else if(slot==22){user.closeInventory();removeNearest(user);}
            return;
        }
        if(slot==31){open(user,"root");return;}
        if(h.screen.equals("healing")){
            if(slot==20){deposit(user);open(user,"healing");return;}
            if(slot==24){user.closeInventory();user.sendMessage(ChatColor.YELLOW+repair(user,false));return;}
        }
        Type[] available=h.screen.equals("weapons")?new Type[]{Type.BALLISTIC,Type.EXPLOSIVE,Type.ADVANCED}:new Type[]{Type.MEDIC,Type.REGEN,Type.INFUSER};
        for(int i=0;i<available.length;i++)if(slot==11+i*2){user.closeInventory();deploy(user,available[i]);return;}
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    private void deposit(Player user){
        if(!hasWrench(user)||user.getLevel()<5){user.sendMessage(ChatColor.RED+"Hold a wrench and have at least 5 levels.");return;}
        PlayerStore.Data d=p.players().get(user.getUniqueId());
        int value=Math.max(1,Math.min(10000,p.getConfig().getInt("engineer.infuser.xp-per-deposit",150)));
        if(d.storedXp>1_000_000-value)return;
        user.giveExpLevels(-5);d.storedXp+=value;p.players().save(d);
        user.sendMessage(ChatColor.GREEN+"Deposited "+value+" reserve units. Engineer reserve: "+d.storedXp);
    }
    public String repair(Player user,boolean confirm){
        Machine station=nearestInfuser(user.getLocation(),8);
        if(station==null)return "No Experience Infuser within 8 blocks.";
        if(!station.owner.equals(user.getUniqueId())&&!p.players().isTrusted(station.owner,user.getUniqueId()))return "Infuser owner has not trusted you.";
        ItemStack hand=user.getInventory().getItemInMainHand();
        if(hand.getType().isAir()||!(hand.getItemMeta() instanceof Damageable meta)||meta.getDamage()<=0)return "Hold a damaged tool, weapon or armor piece.";
        int repairRank=p.players().rank(p.players().get(station.owner),"repair-knowledge");
        double xpPerDamage=Math.max(.01,p.getConfig().getDouble("engineer.infuser.xp-per-durability",1.0));
        int cost=Math.max(1,(int)Math.ceil(meta.getDamage()*xpPerDamage*Math.max(.50,1-.05*repairRank)));
        PlayerStore.Data owner=p.players().get(station.owner());
        if(owner.storedXp<cost)return "Infuser owner needs "+cost+" stored XP units (has "+owner.storedXp+").";
        if(!confirm)return "Repair will cost "+cost+" of "+Bukkit.getOfflinePlayer(station.owner).getName()+"'s reserved XP units. /ub repair confirm";
        // Atomic state update on main thread. Clone preserves every other item tag/enchantment.
        ItemStack fixed=hand.clone();Damageable fixedMeta=(Damageable)fixed.getItemMeta();fixedMeta.setDamage(0);fixed.setItemMeta(fixedMeta);
        user.getInventory().setItemInMainHand(fixed);
        owner.storedXp-=cost;p.players().save(owner);
        return ChatColor.GREEN+"Repaired item using "+cost+" Engineer XP units.";
    }
    /** Owner-configured, capped service: 60s of Regeneration II and Saturation X.
     * Saturation X is amplifier 9. No custom client potion icon is claimed.
     */
    public String infused(Player user){
        Machine station=nearestInfuser(user.getLocation(),8);
        if(station==null)return "No Experience Infuser nearby.";
        if(!station.owner.equals(user.getUniqueId())&&!p.players().isTrusted(station.owner,user.getUniqueId()))return "Infuser owner has not trusted you.";
        if(p.players().rank(p.players().get(station.owner),"infused-technology")<1)return "The machine owner has not unlocked Infused Technology.";
        PlayerStore.Data d=p.players().get(user.getUniqueId());long now=System.currentTimeMillis();
        long remaining=d.cooldowns.getOrDefault("infused-service",0L)-now;
        if(remaining>0)return "Infused cooldown: "+((remaining+999)/1000)+" seconds.";
        int seconds=Math.max(5,Math.min(120,p.getConfig().getInt("engineer.infuser.infused-duration-seconds",60)));
        user.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,seconds*20,1,false,true,true));
        user.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,seconds*20,9,false,true,true));
        d.cooldowns.put("infused-service",now+Math.max(30,p.getConfig().getInt("engineer.infuser.infused-cooldown-seconds",300))*1000L);
        p.players().save(d);return "Infused Technology applied for "+seconds+" seconds.";
    }
    private Machine nearestInfuser(Location l,double range){Machine best=null;double squared=range*range;
        for(Machine m:machines.values())if(m.type==Type.INFUSER){Location pos=m.location();if(pos!=null&&pos.getWorld().equals(l.getWorld())&&pos.distanceSquared(l)<squared){best=m;squared=pos.distanceSquared(l);}}
        return best;
    }
    private void deploy(Player user,Type type){
        if(!p.getConfig().getBoolean("engineer.machines-enabled",true)){user.sendMessage(ChatColor.RED+"Machines disabled in config.");return;}
        if(!hasNode(user,type)||tier(wrench(user.getInventory().getItemInMainHand()))<type.tier){user.sendMessage(ChatColor.RED+"Missing node or required Wrench tier.");return;}
        if(p.getConfig().getStringList("ability.disabled-worlds").contains(user.getWorld().getName()))return;
        int max=Math.max(1,Math.min(20,p.getConfig().getInt("engineer.max-machines-per-owner",3)
            +p.players().rank(p.players().get(user.getUniqueId()),"compact-design")));
        if(machines.values().stream().filter(m->m.owner.equals(user.getUniqueId())).count()>=max){user.sendMessage(ChatColor.RED+"Machine limit reached. Retrieve one first.");return;}
        org.bukkit.util.Vector ahead=user.getLocation().getDirection().setY(0);
        if(ahead.lengthSquared()<0.01)ahead=new org.bukkit.util.Vector(0,0,1);
        Location loc=user.getLocation().clone().add(ahead.normalize().multiply(2.5));loc.setX(Math.floor(loc.getX())+.5);loc.setZ(Math.floor(loc.getZ())+.5);loc.setY(Math.floor(loc.getY()));
        if(!loc.getBlock().isPassable()||loc.getBlock().getRelative(0,-1,0).isPassable()){user.sendMessage(ChatColor.RED+"Deploy on clear solid ground.");return;}
        for(Machine m:machines.values()){Location other=m.location();if(other!=null&&other.getWorld().equals(loc.getWorld())&&other.distanceSquared(loc)<2){user.sendMessage(ChatColor.RED+"Another machine occupies this space.");return;}}
        Machine m=new Machine(UUID.randomUUID(),user.getUniqueId(),loc.getWorld().getUID(),loc.getX(),loc.getY(),loc.getZ(),type);
        machines.put(m.id,m);spawnVisual(m);save();
        user.playSound(loc,Sound.BLOCK_BEACON_ACTIVATE,.7f,1f);
        user.sendMessage(ChatColor.GREEN+type.title+" deployed. Retrieve nearby machines through your Wrench.");
    }
    private void removeNearest(Player user){Machine best=null;double dist=64;
        for(Machine m:machines.values())if(m.owner.equals(user.getUniqueId())){Location l=m.location();if(l!=null&&l.getWorld().equals(user.getWorld())&&l.distanceSquared(user.getLocation())<dist){dist=l.distanceSquared(user.getLocation());best=m;}}
        if(best==null){user.sendMessage(ChatColor.YELLOW+"No owned machine nearby.");return;}
        machines.remove(best.id);BlockDisplay d=visuals.remove(best.id);if(d!=null&&d.isValid())d.remove();save();user.sendMessage(ChatColor.GREEN+"Retrieved "+best.type.title+".");
    }
    private void spawnVisual(Machine m){Location l=m.location();if(l==null)return;
        if(!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))return;
        BlockDisplay existing=visuals.get(m.id);if(existing!=null&&existing.isValid())return;
        BlockDisplay d=l.getWorld().spawn(l,BlockDisplay.class);d.setBlock(Bukkit.createBlockData(m.type.block));
        d.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,m.id.toString());visuals.put(m.id,d);
    }
    private void tick(){ticker++;
        for(Machine m:new ArrayList<>(machines.values())){
            Location loc=m.location();if(loc==null||!loc.getWorld().isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))continue;
            spawnVisual(m);if((ticker*20)%m.type.ticks!=0)continue;
            if(m.type==Type.INFUSER){if(ticker%10==0)loc.getWorld().spawnParticle(Particle.ENCHANT,loc.clone().add(0,.7,0),4,.35,.3,.35,0);continue;}
            Player owner=Bukkit.getPlayer(m.owner);
            if(owner==null||!isEngineer(owner)||!p.getConfig().getBoolean("engineer.machines-enabled",true))continue;
            double range=Math.max(2,Math.min(16,p.getConfig().getDouble("engineer.machine-range",8)
                +p.players().rank(p.players().get(m.owner),"precision-engineering")));
            if(m.type==Type.MEDIC||m.type==Type.REGEN){
                for(Player target:loc.getWorld().getNearbyPlayers(loc,range)){
                    if(!target.getUniqueId().equals(m.owner)&&!p.players().isTrusted(m.owner,target.getUniqueId()))continue;
                    if(m.type==Type.MEDIC){var attr=target.getAttribute(Attribute.MAX_HEALTH);target.setHealth(Math.min(attr==null?20:attr.getValue(),target.getHealth()+Math.max(.1,p.getConfig().getDouble("engineer.medic-heal",1))));}
                    else target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,0,false,false,true));
                }
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,loc.clone().add(0,1,0),5,.3,.3,.3,0);continue;
            }
            if(!loc.getWorld().getPVP()||!p.getConfig().getBoolean("engineer.combat-machines-enabled",false))continue;
            // Conservative combat: only target online enemy players visible to the owner,
            // never bypass trust, spawn, PvP-disabled worlds or owner proximity restrictions.
            Player target=null;double nearest=range*range;
            for(Player other:loc.getWorld().getNearbyPlayers(loc,range)){
                if(other.getUniqueId().equals(m.owner)||p.players().isTrusted(m.owner,other.getUniqueId())||!owner.hasLineOfSight(other))continue;
                if(other.getGameMode()==GameMode.SPECTATOR||other.getGameMode()==GameMode.CREATIVE)continue;
                double dist=other.getLocation().distanceSquared(loc);
                if(dist<nearest){target=other;nearest=dist;}
            }
            if(target==null)continue;
            double damage=Math.max(.1,Math.min(8,p.getConfig().getDouble(m.type==Type.EXPLOSIVE?"engineer.explosive-damage":m.type==Type.ADVANCED?"engineer.advanced-damage":"engineer.ballistic-damage",1.5)
                +p.players().rank(p.players().get(m.owner),"ballistic-training")*.15));
            target.damage(damage,owner);
            loc.getWorld().spawnParticle(m.type==Type.EXPLOSIVE?Particle.EXPLOSION:Particle.CRIT,target.getLocation().add(0,1,0),5,.3,.3,.3,0);
        }
    }
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section=y.getConfigurationSection("machines");if(section==null)return;
        for(String id:section.getKeys(false))try{
            String path="machines."+id+".";Type type=Type.valueOf(y.getString(path+"type"));
            Machine m=new Machine(UUID.fromString(id),UUID.fromString(y.getString(path+"owner")),UUID.fromString(y.getString(path+"world")),y.getDouble(path+"x"),y.getDouble(path+"y"),y.getDouble(path+"z"),type);
            machines.put(m.id,m);
        }catch(Exception ex){p.getLogger().warning("Ignored invalid machine entry "+id);}
    }
    private void save(){YamlConfiguration y=new YamlConfiguration();for(Machine m:machines.values()){
        String path="machines."+m.id+".";y.set(path+"owner",m.owner.toString());y.set(path+"world",m.world.toString());
        y.set(path+"x",m.x);y.set(path+"y",m.y);y.set(path+"z",m.z);y.set(path+"type",m.type.name());
    }
        try{y.save(file);}catch(Exception e){p.getLogger().severe("Could not save Engineer machines: "+e.getMessage());}
    }
    public void removeOwnerMachines(UUID owner){
        for(Machine m:new ArrayList<>(machines.values()))if(m.owner.equals(owner)){
            machines.remove(m.id);BlockDisplay d=visuals.remove(m.id);if(d!=null&&d.isValid())d.remove();
        }
        save();
    }
    public void shutdown(){save();for(BlockDisplay d:visuals.values())if(d.isValid())d.remove();visuals.clear();}
}
