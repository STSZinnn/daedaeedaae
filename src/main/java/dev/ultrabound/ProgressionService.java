package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Persistent, abuse-resistant universal skill-point earning. */
public final class ProgressionService implements Listener {
    private final UltraboundPlugin p;
    ProgressionService(UltraboundPlugin p){this.p=p;}
    @EventHandler(priority=EventPriority.MONITOR)
    public void advancement(PlayerAdvancementDoneEvent e){
        if(!p.getConfig().getBoolean("progression.enabled",true))return;
        Player player=e.getPlayer();PlayerStore.Data data=p.players().get(player.getUniqueId());
        if(data.skillset==null)return;
        String key=e.getAdvancement().getKey().toString();
        if(key.contains(":recipes/")||key.startsWith("minecraft:recipes/"))return;
        if(!data.rewardedAdvancements.add(key))return;
        grant(player,data,Math.max(0,Math.min(10,p.getConfig().getInt("progression.points-per-advancement",1))),"advancement");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void pvpKill(PlayerDeathEvent e){
        if(!p.getConfig().getBoolean("progression.enabled",true))return;
        Player victim=e.getEntity(),killer=victim.getKiller();
        if(killer==null||killer.equals(victim)||!victim.getWorld().getPVP())return;
        PlayerStore.Data playerData=p.players().get(killer.getUniqueId());
        PlayerStore.Data victimData=p.players().get(victim.getUniqueId());
        if(playerData.skillset==null||p.players().isTrusted(killer.getUniqueId(),victim.getUniqueId()))return;
        long now=System.currentTimeMillis();
        long cooldown=Math.max(60,Math.min(86400,p.getConfig().getLong("progression.same-pair-kill-cooldown-seconds",3600)))*1000L;
        if(now-playerData.rewardedKillPairs.getOrDefault(victim.getUniqueId().toString(),0L)<cooldown)return;
        if(now-victimData.rewardedKillPairs.getOrDefault(killer.getUniqueId().toString(),0L)<cooldown)return;
        playerData.rewardedKillPairs.put(victim.getUniqueId().toString(),now);
        victimData.rewardedKillPairs.put(killer.getUniqueId().toString(),now);
        grant(killer,playerData,Math.max(0,Math.min(5,p.getConfig().getInt("progression.points-per-legitimate-pvp-kill",1))),"PvP victory");
        p.players().save(victimData);
    }
    private void grant(Player user,PlayerStore.Data data,int amount,String reason){
        if(amount>0){data.availablePoints=Math.min(1_000_000,data.availablePoints+amount);
            data.earnedPoints=Math.min(1_000_000,data.earnedPoints+amount);
            user.sendMessage(ChatColor.AQUA+"+"+amount+" Ultrabound skill point(s) from "+reason+"!");}
        p.players().save(data);
    }
}
