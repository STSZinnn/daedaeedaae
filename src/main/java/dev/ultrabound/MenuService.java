package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Richer GUI system for class selection, helpbook, and skill-tree browsing.
 * Menus remain server-side inventories so GUI items cannot be extracted.
 */
public final class MenuService implements Listener {
    static final class Holder implements InventoryHolder {
        final String screen, cls, branch;
        final boolean changingClass;
        final Map<Integer, String> choices = new HashMap<>();
        Inventory inventory;
        Holder(String screen, String cls, String branch, boolean changingClass) {
            this.screen = screen;
            this.cls = cls;
            this.branch = branch;
            this.changingClass = changingClass;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final UltraboundPlugin p;
    private static final String BLANK = ChatColor.DARK_GRAY + "•";

    MenuService(UltraboundPlugin plugin) { p = plugin; }

    private Inventory make(Holder h, int size, String name) {
        h.inventory = Bukkit.createInventory(h, size, ChatColor.DARK_AQUA + "Ultrabound » " + name);
        return h.inventory;
    }

    private ItemStack icon(Material mat, String name, String... description) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String s : description) lore.add(ChatColor.GRAY + s);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    private ItemStack icon(Material mat, String name, List<String> description) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        for (String s : description) lore.add(s.startsWith("§") ? s : ChatColor.GRAY + s);
        m.setLore(lore);
        item.setItemMeta(m);
        return item;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }

    private void fillRow(Inventory inv, int row, Material mat) {
        for (int i = row * 9; i < row * 9 + 9; i++) inv.setItem(i, pane(mat));
    }

    private void frame(Inventory inv, Material border, Material accent) {
        fillRow(inv, 0, border);
        fillRow(inv, inv.getSize() / 9 - 1, border);
        for (int row = 1; row < inv.getSize() / 9 - 1; row++) {
            inv.setItem(row * 9, pane(border));
            inv.setItem(row * 9 + 8, pane(border));
        }
        if (inv.getSize() >= 54) {
            inv.setItem(4, pane(accent));
            inv.setItem(49, pane(accent));
        }
    }

    private void nav(Inventory i) {
        i.setItem(i.getSize() - 1, icon(Material.BARRIER, ChatColor.RED + "Back / Close",
            "Close this menu or go back from the current screen."));
    }

    private Material categoryPane(String category) {
        return switch (String.valueOf(category).toUpperCase(Locale.ROOT)) {
            case "PVP" -> Material.RED_STAINED_GLASS_PANE;
            case "SUPPORT" -> Material.LIME_STAINED_GLASS_PANE;
            case "GRINDING" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "PVP-GRIND", "PVP_GRIND" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.CYAN_STAINED_GLASS_PANE;
        };
    }

    private ChatColor categoryColor(String category) {
        return switch (String.valueOf(category).toUpperCase(Locale.ROOT)) {
            case "PVP" -> ChatColor.RED;
            case "SUPPORT" -> ChatColor.GREEN;
            case "GRINDING" -> ChatColor.GOLD;
            case "PVP-GRIND", "PVP_GRIND" -> ChatColor.YELLOW;
            default -> ChatColor.AQUA;
        };
    }

    private Material branchMaterial(String branchId) {
        String id = branchId.toLowerCase(Locale.ROOT);
        if (id.contains("mechanic")) return Material.TRIPWIRE_HOOK;
        if (id.contains("maintenance") || id.contains("restoration") || id.contains("medical")) return Material.GOLDEN_APPLE;
        if (id.contains("explos") || id.contains("demolition") || id.contains("bomb")) return Material.TNT;
        if (id.contains("prospect") || id.contains("resource") || id.contains("craft")) return Material.EMERALD;
        if (id.contains("deep") || id.contains("shadow") || id.contains("stealth")) return Material.ENDER_EYE;
        if (id.contains("guardian") || id.contains("defen")) return Material.SHIELD;
        if (id.contains("alchemy") || id.contains("brew")) return Material.POTION;
        if (id.contains("aviat") || id.contains("wind") || id.contains("air")) return Material.FEATHER;
        if (id.contains("beast") || id.contains("pack") || id.contains("bond")) return Material.BONE;
        if (id.contains("enchant") || id.contains("arcane") || id.contains("mystic")) return Material.ENCHANTED_BOOK;
        if (id.contains("villager") || id.contains("trade")) return Material.EMERALD_BLOCK;
        if (id.contains("melee") || id.contains("combat") || id.contains("weapon")) return Material.IRON_SWORD;
        if (id.contains("arch") || id.contains("bow")) return Material.BOW;
        if (id.contains("miner") || id.contains("excavation")) return Material.IRON_PICKAXE;
        return Material.BOOK;
    }

    private List<String> starterPreview(String skillsetId) {
        return switch (skillsetId) {
            case "MINER" -> List.of("§7Starter kit:", "§f• Efficiency III Iron Pickaxe", "§f• 32 Torches", "§f• 16 Bread", "§f• Crafting Table");
            case "BOMBARDEER" -> List.of("§7Starter kit:", "§f• Infinite TNT", "§8Auto-ignites, never consumed.");
            case "GUARDIAN" -> List.of("§7Starter kit:", "§f• Bound Guardian Shield", "§8Returns on death if rules allow.");
            case "ALCHEMIST" -> List.of("§7Starter focus:", "§f• Potion utility", "§f• Infusium mechanics", "§8Trusted players are protected.");
            case "ENGINEER" -> List.of("§7Starter focus:", "§f• Wrench progression", "§f• Machine branch unlocks", "§8Medic Turret showcase included.");
            case "ENCHANTER" -> List.of("§7Starter kit:", "§f• Enchanting Table", "§f• 3 Bookshelves", "§f• 16 Lapis Lazuli", "§f• 16 Experience Bottles");
            default -> List.of("§7Starter kit and final values", "§7are defined in §bskillsets.yml§7.", "§8This preview menu shows class identity,", "§8not every exact item in live detail.");
        };
    }

    private String classIdentity(String skillsetId) {
        return switch (skillsetId) {
            case "MINER" -> "Resource gathering, ore utility, and underground exploration.";
            case "BOMBARDEER" -> "Explosive pressure, area control, and mobility bursts.";
            case "MELEE" -> "Frontline close-combat pressure with weapon mastery.";
            case "VILLAGER_MASTER" -> "Trade optimization, village value, and economy utility.";
            case "ARCHER" -> "Ranged precision, spacing, and bow-based offense.";
            case "GUARDIAN" -> "Durable defensive duelist anchored around the bound shield.";
            case "ASSASSIN" -> "Stealth, repositioning, and burst opportunities.";
            case "ALCHEMIST" -> "Potion infusions, support, and controlled debuff utility.";
            case "AVIATOR" -> "Mobility, aerial control, and wind-based movement.";
            case "ENCHANTER" -> "Gear progression and enchantment-focused upgrades.";
            case "BEAST_TAMER" -> "Companion support and PvP utility through tame allies.";
            case "ENGINEER" -> "Machines, wrench progression, and deployable support systems.";
            default -> "Distinct progression class.";
        };
    }

    private String implementationStatus(SkillRegistry.Skillset s) {
        long implemented = s.actives().values().stream().filter(SkillRegistry.Active::implemented).count();
        return implemented == 0 ? "Mostly design-preview content" : implemented + "/" + s.actives().size() + " active entries playable in showcase";
    }

    private int branchSpent(PlayerStore.Data d, SkillRegistry.Branch branch) {
        int total = 0;
        for (SkillRegistry.Node node : branch.nodes().values()) total += p.players().rank(d, node.id()) * node.cost();
        return total;
    }

    private int implementedNodes(SkillRegistry.Branch branch) {
        int c = 0;
        for (SkillRegistry.Node node : branch.nodes().values()) if (node.implemented()) c++;
        return c;
    }

    private List<String> previewNodes(SkillRegistry.Branch branch) {
        List<String> names = new ArrayList<>();
        for (SkillRegistry.Node n : branch.nodes().values()) {
            names.add(n.name());
            if (names.size() >= 3) break;
        }
        return names;
    }

    public void choose(Player player, boolean changing) {
        Holder h = new Holder("choose", null, null, changing);
        Inventory inv = make(h, 54, changing ? "Change Skillset" : "Choose Skillset");
        frame(inv, Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(4, icon(Material.NETHER_STAR, ChatColor.AQUA + "Ultrabound Skillsets",
            List.of("§7Choose one of the §b12 skillsets §7for your SMP journey.",
                "§7Each class has its own §fidentity§7, §fbranches§7,",
                "§factive abilities§7, and progression path.",
                BLANK,
                changing ? "§eYou are using class-change mode." : "§aFirst selection is free.",
                changing ? "§7A valid reset token is consumed on confirm." : "§7Once chosen, class is locked until reset.",
                "§8Click any skillset below to inspect it first.")));
        inv.setItem(49, icon(Material.BOOK, ChatColor.YELLOW + "Selection Help",
            "Inspect a skillset before confirming it.",
            "You will see its role, branch count, active count,",
            "and a quick starter preview."));

        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        int i = 0;
        for (SkillRegistry.Skillset s : p.skills().all()) {
            if (i >= slots.length) break;
            List<String> lore = new ArrayList<>();
            lore.add(categoryColor(s.category()) + "Category: " + s.category());
            lore.add("§7" + classIdentity(s.id()));
            lore.add(BLANK);
            lore.add("§fBranches: §b" + s.branches().size());
            lore.add("§fActive abilities: §d" + s.actives().size());
            lore.add("§fShowcase status: §e" + implementationStatus(s));
            lore.add(BLANK);
            lore.addAll(starterPreview(s.id()));
            lore.add(BLANK);
            lore.add("§aClick to inspect and confirm.");
            inv.setItem(slots[i], icon(s.icon(), categoryColor(s.category()) + s.name(), lore));
            h.choices.put(slots[i], s.id());
            i++;
        }
        nav(inv);
        player.openInventory(inv);
    }

    private void confirm(Player player, String id, boolean changing) {
        SkillRegistry.Skillset s = p.skills().get(id);
        if (s == null) return;
        Holder h = new Holder("confirm", s.id(), null, changing);
        Inventory inv = make(h, 54, "Confirm " + s.name());
        frame(inv, categoryPane(s.category()), Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        List<String> centerLore = new ArrayList<>();
        centerLore.add("§7" + classIdentity(s.id()));
        centerLore.add("§fCategory: " + categoryColor(s.category()) + s.category());
        centerLore.add("§fBranches: §b" + s.branches().size());
        centerLore.add("§fActive abilities: §d" + s.actives().size());
        centerLore.add(BLANK);
        centerLore.addAll(starterPreview(s.id()));
        centerLore.add(BLANK);
        centerLore.add(changing ? "§eReset token will be consumed on confirm." : "§cClass cannot be changed without a reset token.");
        inv.setItem(13, icon(s.icon(), categoryColor(s.category()) + s.name(), centerLore));

        inv.setItem(11, icon(Material.LIME_CONCRETE, ChatColor.GREEN + "Confirm Selection",
            "Choose " + s.name() + " as your skillset.",
            changing ? "Consumes the reset token in your main hand." : "Starter kit is granted once for this class.",
            "Click to finalize."));
        inv.setItem(15, icon(Material.RED_CONCRETE, ChatColor.RED + "Cancel",
            "Return to the skillset browser without making changes."));

        int slot = 28;
        for (SkillRegistry.Branch b : s.branches().values()) {
            if (slot > 34) break;
            List<String> lore = new ArrayList<>();
            lore.add("§7Branch preview");
            lore.add("§fNodes: §b" + b.nodes().size());
            lore.add("§fImplemented nodes: §a" + implementedNodes(b));
            List<String> examples = previewNodes(b);
            if (!examples.isEmpty()) lore.add("§8Examples: " + String.join(", ", examples));
            inv.setItem(slot++, icon(branchMaterial(b.id()), ChatColor.YELLOW + b.name(), lore));
        }
        nav(inv);
        player.openInventory(inv);
    }

    public void help(Player player) {
        Holder h = new Holder("help", null, null, false);
        Inventory inv = make(h, 54, "Helpbook");
        frame(inv, Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(4, icon(Material.WRITABLE_BOOK, ChatColor.AQUA + "Ultrabound Helpbook",
            "Browse every class overview.",
            "Click a skillset to see branches and active abilities."));
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23};
        int i = 0;
        for (SkillRegistry.Skillset s : p.skills().all()) {
            if (i >= slots.length) break;
            inv.setItem(slots[i], icon(s.icon(), ChatColor.AQUA + s.name(),
                "Category: " + s.category(),
                "Identity: " + classIdentity(s.id()),
                "Click for detailed guide view."));
            h.choices.put(slots[i], s.id());
            i++;
        }
        nav(inv);
        player.openInventory(inv);
    }

    public void guide(Player player, String cls) {
        SkillRegistry.Skillset s = p.skills().get(cls);
        if (s == null) return;
        Holder h = new Holder("guide", s.id(), null, false);
        Inventory inv = make(h, 54, s.name() + " Guide");
        frame(inv, categoryPane(s.category()), Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + classIdentity(s.id()));
        lore.add("§fCategory: " + categoryColor(s.category()) + s.category());
        lore.add("§fBranches: §b" + s.branches().size());
        lore.add("§fActive abilities: §d" + s.actives().size());
        lore.add("§fShowcase status: §e" + implementationStatus(s));
        lore.add(BLANK);
        lore.addAll(starterPreview(s.id()));
        inv.setItem(4, icon(s.icon(), ChatColor.AQUA + s.name(), lore));

        int slot = 10;
        for (SkillRegistry.Branch b : s.branches().values()) {
            List<String> branchLore = new ArrayList<>();
            branchLore.add("§7Passive branch overview.");
            branchLore.add("§fNode count: §b" + b.nodes().size());
            branchLore.add("§fImplemented nodes: §a" + implementedNodes(b));
            List<String> examples = previewNodes(b);
            if (!examples.isEmpty()) branchLore.add("§8Examples: " + String.join(", ", examples));
            branchLore.add(BLANK);
            branchLore.add("§eClick to browse nodes.");
            inv.setItem(slot, icon(branchMaterial(b.id()), ChatColor.YELLOW + b.name(), branchLore));
            h.choices.put(slot, b.id());
            slot++;
        }
        inv.setItem(31, icon(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + "Active Abilities",
            "Open the active ability browser for this skillset.",
            "Implemented actives can be equipped into slot 1 or 2."));
        nav(inv);
        player.openInventory(inv);
    }

    public void tree(Player player) {
        PlayerStore.Data data = p.players().get(player.getUniqueId());
        SkillRegistry.Skillset s = p.skills().get(data.skillset);
        if (s == null) { player.sendMessage(ChatColor.RED + "Choose a class first."); return; }

        Holder h = new Holder("tree", s.id(), null, false);
        Inventory inv = make(h, 54, "Skill Tree • " + s.name());
        frame(inv, categoryPane(s.category()), Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        List<String> summary = new ArrayList<>();
        summary.add("§7" + classIdentity(s.id()));
        summary.add("§fAvailable points: §a" + data.availablePoints);
        summary.add("§fEarned points: §b" + data.earnedPoints);
        summary.add("§fAllocated points: §e" + data.allocatedPoints);
        summary.add("§fAllocation cap: §6" + ("ENGINEER".equals(s.id()) ? "No normal global cap" : s.allocationCap()));
        summary.add("§fBranch cap: §6" + ("ENGINEER".equals(s.id()) ? "Branch unlock system" : s.branchCap()));
        summary.add(BLANK);
        summary.add("§8Browse branches below to inspect or buy passive nodes.");
        inv.setItem(4, icon(s.icon(), ChatColor.AQUA + s.name() + " Progression", summary));

        inv.setItem(47, icon(Material.EMERALD, ChatColor.GREEN + "Points Overview",
            "Available: " + data.availablePoints,
            "Earned: " + data.earnedPoints,
            "Allocated: " + data.allocatedPoints,
            "Progression data is saved per player."));
        inv.setItem(49, icon(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + "Equipped Actives",
            "Open your current active-ability loadout.",
            "Left click equips to slot 1, right click to slot 2."));
        inv.setItem(51, icon(Material.KNOWLEDGE_BOOK, ChatColor.YELLOW + "Tree Notes",
            "Implemented nodes can be purchased.",
            "Preview-only nodes stay visible for planning,",
            "but cannot be bought until their effect exists."));

        int[] slots = {19,20,21,22,23,24};
        int i = 0;
        for (SkillRegistry.Branch branch : s.branches().values()) {
            if (i >= slots.length) break;
            boolean locked = s.id().equals("ENGINEER") && !"mechanics".equals(branch.id()) && !data.unlockedBranches.contains(branch.id());
            int spent = branchSpent(data, branch);
            int max = s.id().equals("ENGINEER") ? Integer.MAX_VALUE : s.branchCap();
            List<String> lore = new ArrayList<>();
            lore.add("§fNodes: §b" + branch.nodes().size());
            lore.add("§fImplemented: §a" + implementedNodes(branch));
            lore.add("§fAllocated here: §e" + spent + (max == Integer.MAX_VALUE ? "" : "/" + max));
            List<String> examples = previewNodes(branch);
            if (!examples.isEmpty()) lore.add("§8Examples: " + String.join(", ", examples));
            lore.add(BLANK);
            if (locked) {
                lore.add("§cLOCKED");
                lore.add("§7Engineer must unlock this branch with points first.");
                lore.add("§eClick to unlock if you have enough points.");
            } else {
                lore.add("§aClick to browse nodes in this branch.");
            }
            inv.setItem(slots[i], icon(locked ? Material.BARRIER : branchMaterial(branch.id()),
                (locked ? ChatColor.RED : ChatColor.AQUA) + branch.name(), lore));
            h.choices.put(slots[i], branch.id());
            i++;
        }
        nav(inv);
        player.openInventory(inv);
    }

    private void nodes(Player player, String cls, String branch) {
        SkillRegistry.Skillset s = p.skills().get(cls); if (s == null) return;
        SkillRegistry.Branch b = s.branches().get(branch); if (b == null) return;
        Holder h = new Holder("nodes", s.id(), branch, false);
        Inventory inv = make(h, 54, b.name() + " Nodes");
        frame(inv, categoryPane(s.category()), Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        PlayerStore.Data d = p.players().get(player.getUniqueId());

        inv.setItem(4, icon(branchMaterial(branch), ChatColor.YELLOW + b.name(),
            List.of("§7Passive branch browser.",
                "§fNodes: §b" + b.nodes().size(),
                "§fImplemented nodes: §a" + implementedNodes(b),
                "§fAllocated in branch: §e" + branchSpent(d, b) + ("ENGINEER".equals(s.id()) ? "" : "/" + s.branchCap()),
                BLANK,
                "§8Select a node below to inspect and buy it.")));
        inv.setItem(49, icon(Material.ARROW, ChatColor.AQUA + "Back to Skill Tree",
            "Return to the previous class tree overview."));
        inv.setItem(53, icon(Material.BARRIER, ChatColor.RED + "Close"));

        int[] slots = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
        };
        int n = 0;
        for (SkillRegistry.Node node : b.nodes().values()) {
            if (n >= slots.length) break;
            int slot = slots[n++];
            int current = p.players().rank(d, node.id());
            boolean maxed = current >= node.maxRank();
            boolean preview = !node.implemented();
            Material material = preview ? Material.BARRIER : maxed ? Material.EMERALD_BLOCK : current > 0 ? Material.EMERALD : Material.AMETHYST_SHARD;
            String status = preview ? "§cDesign preview only" : maxed ? "§aMaxed" : "§eClick to purchase";
            String currentValue = String.format(Locale.US, "%.2f", current * node.perRank());
            String nextValue = String.format(Locale.US, "%.2f", Math.min(node.maxRank(), current + 1) * node.perRank());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + node.description());
            lore.add(BLANK);
            lore.add("§fRank: §b" + current + "§7 / §b" + node.maxRank());
            lore.add("§fNext cost: §6" + node.cost());
            lore.add("§fCurrent value: §a" + currentValue);
            lore.add("§fNext value: §a" + nextValue);
            lore.add("§fMechanic key: §8" + node.mechanic());
            lore.add("§fPrerequisites: §7" + (node.prerequisites().isEmpty() ? "None" : node.prerequisites()));
            lore.add(BLANK);
            lore.add(status);
            inv.setItem(slot, icon(material, ChatColor.AQUA + node.name(), lore));
            h.choices.put(slot, node.id());
        }
        player.openInventory(inv);
    }

    private void actives(Player player, String cls) {
        SkillRegistry.Skillset s = p.skills().get(cls); if (s == null) return;
        Holder h = new Holder("actives", s.id(), null, false);
        Inventory inv = make(h, 54, "Active Abilities");
        frame(inv, categoryPane(s.category()), Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        PlayerStore.Data d = p.players().get(player.getUniqueId());

        inv.setItem(4, icon(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + s.name() + " Actives",
            List.of("§7Browse and equip active abilities.",
                "§fSlot 1: §b" + (d.equipped.size() >= 1 ? d.equipped.get(0) : "empty"),
                "§fSlot 2: §b" + (d.equipped.size() >= 2 ? d.equipped.get(1) : "empty"),
                BLANK,
                "§eLeft click = equip to slot 1",
                "§eRight click = equip to slot 2")));
        inv.setItem(49, icon(Material.ARROW, ChatColor.AQUA + "Back to Tree",
            "Return to the class progression menu."));
        inv.setItem(53, icon(Material.BARRIER, ChatColor.RED + "Close"));

        int[] slots = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
        };
        int n = 0;
        for (SkillRegistry.Active a : s.actives().values()) {
            if (n >= slots.length) break;
            int slot = slots[n++];
            int existing = d.equipped.indexOf(a.id());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + a.description());
            lore.add(BLANK);
            lore.add("§fType: §d" + a.type());
            lore.add("§fCooldown: §6" + a.cooldown() + "s");
            lore.add("§fDuration: §6" + a.duration() + "s");
            lore.add("§fState: " + (a.implemented() ? "§aPlayable in showcase" : "§cDesign preview only"));
            lore.add(existing >= 0 ? "§bEquipped in slot " + (existing + 1) : "§7Not equipped");
            lore.add(BLANK);
            lore.add(a.implemented() ? "§eLeft click slot 1 • Right click slot 2" : "§8Cannot equip until implemented");
            inv.setItem(slot, icon(a.implemented() ? Material.BLAZE_POWDER : Material.BARRIER, ChatColor.AQUA + a.name(), lore));
            h.choices.put(slot, a.id());
        }
        player.openInventory(inv);
    }

    public void recipes(Player player) {
        Holder h = new Holder("recipes", null, null, false);
        Inventory inv = make(h, 27, "Recipe Browser");
        inv.setItem(10, icon(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Infusium", "Special Alchemist catalyst.", "Use /ultrabound infuse with two potions."));
        inv.setItem(12, icon(Material.SHIELD, ChatColor.AQUA + "Guardian Shield", "Bound to one Guardian and returned on death."));
        inv.setItem(14, icon(Material.TNT, ChatColor.GOLD + "Infinite TNT", "Bombardeer starter item only."));
        inv.setItem(16, icon(Material.TRIPWIRE_HOOK, ChatColor.YELLOW + "Engineer Wrenches",
            "Spear-style diagonal: material, material, stick.", "Stone / Iron / Diamond / Netherite.",
            "Right-click to browse; Medic Turret prototype is playable."));
        nav(inv);
        player.openInventory(inv);
    }

    public void adminItems(Player player) {
        Holder h = new Holder("items", null, null, false);
        Inventory inv = make(h, 27, "Admin Items");
        String[] choices = {CustomItems.RESET_CLASS, CustomItems.RESET_TREE, CustomItems.INFUSIUM, CustomItems.INFIN_TNT, CustomItems.GUARDIAN_SHIELD};
        for (int i = 0; i < choices.length; i++) {
            int slot = 10 + i;
            String id = choices[i];
            inv.setItem(slot, p.items().make(id, id.equals(CustomItems.GUARDIAN_SHIELD) || id.equals(CustomItems.INFIN_TNT) ? player.getUniqueId() : null));
            h.choices.put(slot, id);
        }
        nav(inv);
        player.openInventory(inv);
    }

    public void adminConfig(Player player) {
        Holder h = new Holder("config", null, null, false);
        Inventory inv = make(h, 27, "Admin Settings");
        inv.setItem(10, icon(Material.COMPARATOR, ChatColor.YELLOW + "Progression", "Edit safely: /ub set global progression..."));
        inv.setItem(12, icon(Material.BOOK, ChatColor.YELLOW + "Skillsets", "Nodes live in skillsets.yml", "Reload with /ub reload"));
        inv.setItem(14, icon(Material.REDSTONE, ChatColor.YELLOW + "Ability Values", "Edit in skillsets.yml", "Reload with /ub reload"));
        inv.setItem(16, icon(Material.PAPER, ChatColor.YELLOW + "Implementation Status", "Check implementation-status.yml before testing."));
        nav(inv);
        player.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        // shared back/close behavior
        if (h.screen.equals("nodes") && slot == 49) { tree(player); return; }
        if (h.screen.equals("actives") && slot == 49) { tree(player); return; }
        if (slot == e.getView().getTopInventory().getSize() - 1 || (h.screen.equals("nodes") && slot == 53) || (h.screen.equals("actives") && slot == 53)) {
            player.closeInventory();
            return;
        }

        String choice = h.choices.get(slot);
        switch (h.screen) {
            case "choose" -> { if (choice != null) confirm(player, choice, h.changingClass); }
            case "confirm" -> {
                if (slot == 15) { choose(player, h.changingClass); return; }
                if (slot != 11) return;
                PlayerStore.Data d = p.players().get(player.getUniqueId());
                if (h.changingClass) {
                    if (d.skillset == null || !p.items().is(player.getInventory().getItemInMainHand(), CustomItems.RESET_CLASS)) {
                        player.sendMessage(ChatColor.RED + "Hold a valid reset token in your main hand.");
                        return;
                    }
                    ItemStack token = player.getInventory().getItemInMainHand();
                    token.setAmount(token.getAmount() - 1);
                } else if (d.skillset != null) {
                    player.sendMessage(ChatColor.RED + "Already selected. Use a Reset Token.");
                    return;
                }
                p.players().changeClass(d, h.cls);
                p.items().grantKit(player, h.cls);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "You are now " + p.skills().get(h.cls).name() + "!");
            }
            case "help" -> { if (choice != null) guide(player, choice); }
            case "guide" -> {
                if (slot == 31) actives(player, h.cls);
                else if (choice != null) nodes(player, h.cls, choice);
            }
            case "tree" -> {
                if (slot == 49) { actives(player, h.cls); return; }
                else if (choice != null) {
                    PlayerStore.Data data = p.players().get(player.getUniqueId());
                    if (h.cls.equals("ENGINEER") && !"mechanics".equals(choice) && !data.unlockedBranches.contains(choice)) {
                        player.sendMessage(ChatColor.YELLOW + p.players().unlockBranch(data, choice));
                        tree(player);
                    } else nodes(player, h.cls, choice);
                }
            }
            case "nodes" -> {
                if (choice == null) return;
                player.sendMessage(ChatColor.AQUA + p.players().buy(p.players().get(player.getUniqueId()), choice));
                nodes(player, h.cls, h.branch);
            }
            case "actives" -> {
                if (choice != null) {
                    String result = p.abilities().equip(player, choice, e.isRightClick() ? 2 : 1);
                    player.sendMessage(ChatColor.YELLOW + result);
                    actives(player, h.cls);
                }
            }
            case "items" -> {
                if (!player.hasPermission("ultrabound.admin")) return;
                if (choice != null) {
                    UUID owner = choice.equals(CustomItems.GUARDIAN_SHIELD) || choice.equals(CustomItems.INFIN_TNT) ? player.getUniqueId() : null;
                    p.getServer().getScheduler().runTask(p, () -> {
                        var leftovers = player.getInventory().addItem(p.items().make(choice, owner));
                        if (leftovers.isEmpty()) player.sendMessage(ChatColor.GREEN + "Granted " + choice);
                        else player.sendMessage(ChatColor.RED + "Inventory full; not granted.");
                    });
                }
            }
            default -> {}
        }
    }

    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
