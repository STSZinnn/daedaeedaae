package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Live, event-driven passives only. Unimplemented draft nodes are never purchasable. */
public final class PassiveService implements Listener {
    private final UltraboundPlugin plugin;
    private final BukkitTask task;
    PassiveService(UltraboundPlugin p){plugin=p;task=Bukkit.getScheduler().runTaskTimer(p,this::pulse,40,40);}
    private PlayerStore.Data data(Player p){return plugin.players().get(p.getUniqueId());}
    private int rank(Player p,String key){return plugin.players().rank(data(p),key);}
    private boolean cls(Player p,String id){return id.equals(data(p).skillset);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void itemDamage(PlayerItemDamageEvent e){
        Player p=e.getPlayer();ItemStack item=e.getItem();
        if(!cls(p,"MINER") || !item.getType().name().endsWith("_PICKAXE"))return;
        int ranks=rank(p,"durable-tools")+rank(p,"efficient-swing");
        double chance=Math.min(.60,.03*ranks);
        if(ranks>0 && Math.random()<chance)e.setDamage(Math.max(0,e.getDamage()-1));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void damaged(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p))return;
        double reduction=0;
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL){
            if(cls(p,"MINER") && p.getLocation().getY()<0) reduction+=rank(p,"lightweight")*.05;
            if(cls(p,"ARCHER"))reduction+=rank(p,"sure-footed")*.04;
            if(cls(p,"AVIATOR"))reduction+=rank(p,"soft-landing")*.06+rank(p,"featherweight")*.03;
        }
        if(cls(p,"BOMBARDEER") && (e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION))
            reduction+=rank(p,"blast-awareness")*.05;
        if(cls(p,"MINER") && p.getLocation().getY()<0 && e instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Monster)
            reduction+=rank(p,"cave-dweller")*.03;
        if(cls(p,"VILLAGER_MASTER") && rank(p,"community-trust")>0){
            boolean nearVillager=!p.getWorld().getNearbyEntitiesByType(Villager.class,p.getLocation(),8).isEmpty();
            if(nearVillager)reduction+=rank(p,"community-trust")*.02;
        }
        if(cls(p,"GUARDIAN") && rank(p,"nearby-guard")>0){
            boolean guarded=false;
            for(Player ally:p.getWorld().getNearbyPlayers(p.getLocation(),5)){
                if(!ally.equals(p)&&plugin.players().isTrusted(p.getUniqueId(),ally.getUniqueId())){guarded=true;break;}
            }
            if(guarded)reduction+=rank(p,"nearby-guard")*.02;
        }
        if(reduction>0)e.setDamage(Math.max(0,e.getDamage()*(1-Math.min(.5,reduction))));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player attacker && e.getEntity() instanceof LivingEntity victim){
            if(cls(attacker,"MELEE") && attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")){
                double bonus=rank(attacker,"sharp-handling")*.02;
                var max=victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if(max!=null && victim.getHealth()<max.getValue()*.3)bonus+=rank(attacker,"finisher")*.03;
                if(bonus>0)e.setDamage(e.getDamage()*(1+Math.min(.3,bonus)));
            }else if(cls(attacker,"ENGINEER") && rank(attacker,"combat-training")>0){
                e.setDamage(e.getDamage()*(1+Math.min(.15,rank(attacker,"combat-training")*.02)));
            }
        }
        if(e.getDamager() instanceof Wolf wolf && wolf.getOwner() instanceof Player owner
            && cls(owner,"BEAST_TAMER") && rank(owner,"wolf-training")>0){
            if(e.getEntity() instanceof Player target && plugin.players().isTrusted(owner.getUniqueId(),target.getUniqueId())){
                e.setCancelled(true);return;
            }
            e.setDamage(e.getDamage()*(1+Math.min(.25,rank(owner,"wolf-training")*.04)));
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void potionDrink(PlayerItemConsumeEvent e){
        Player p=e.getPlayer();
        if(cls(p,"ALCHEMIST") && e.getItem().getType()==Material.POTION && rank(p,"restorative-draft")>0){
            int duration=20*(2+rank(p,"restorative-draft"));
            Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,duration,0,true,false,true));});
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void enchant(EnchantItemEvent e){
        Player p=e.getEnchanter();if(!cls(p,"ENCHANTER"))return;
        int rank=rank(p,"arcane-savings");
        if(rank>0)e.setExpLevelCost(Math.max(1,e.getExpLevelCost()-rank));
    }
    private void pulse(){
        for(Player p:Bukkit.getOnlinePlayers()){
            PlayerStore.Data d=data(p);
            if("MINER".equals(d.skillset) && rank(p,"tunnel-vision")>0 && p.getLocation().getY()<0
                && p.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")){
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,75,0,true,false,false));
            }
            if("ENGINEER".equals(d.skillset) && rank(p,"lightweight-equipment")>0
                && String.valueOf(plugin.items().identity(p.getInventory().getItemInMainHand())).startsWith("wrench_")){
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,75,0,true,false,false));
            }
            if("AVIATOR".equals(d.skillset) && rank(p,"calm-descent")>0 && p.getFallDistance()>3 && !p.isOnGround()){
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,48,0,true,false,false));
            }
        }
    }
    public void shutdown(){task.cancel();}
}
