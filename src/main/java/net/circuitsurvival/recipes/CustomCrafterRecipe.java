package net.circuitsurvival.recipes;

import net.circuitsurvival.items.IntermediateMaterials;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * カスタムクラフター用レシピ定義。
 * スロット0-8の3x3グリッドを使った成形レシピ。
 */
public record CustomCrafterRecipe(
        String id,
        String[] shape,          // 3行のパターン文字列 ("ABC","DEF","GHI")
        Map<Character, Ingredient> ingredients,  // 文字→材料マップ
        ItemStack result
) {

    /** 材料定義: 素材IDまたはバニラMaterialのどちらかで指定 */
    public record Ingredient(String materialId, Material vanilla) {
        public static Ingredient ofId(String id) { return new Ingredient(id, null); }
        public static Ingredient of(Material m) { return new Ingredient(null, m); }
        public static Ingredient air() { return new Ingredient(null, Material.AIR); }
    }

    // ---- 全レシピレジストリ ------------------------------------------------

    private static final List<CustomCrafterRecipe> RECIPES = new ArrayList<>();

    public static void register(CustomCrafterRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<CustomCrafterRecipe> getAll() {
        return RECIPES;
    }

    /**
     * スロット0-8の内容から一致するレシピを検索する。
     * 中間素材のPDC-IDとバニラMaterialの両方をチェックする。
     */
    public static CustomCrafterRecipe match(ItemStack[] slots) {
        if (slots == null) return null;
        for (CustomCrafterRecipe r : RECIPES) {
            if (matches(r, slots)) return r;
        }
        return null;
    }

    private static boolean matches(CustomCrafterRecipe r, ItemStack[] slots) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIdx = row * 3 + col;
                if (slotIdx >= slots.length) return false;
                char patternChar = (row < r.shape.length && col < r.shape[row].length())
                        ? r.shape[row].charAt(col) : ' ';
                Ingredient ing = r.ingredients.getOrDefault(patternChar, Ingredient.air());
                ItemStack slotItem = slots[slotIdx];

                if (ing.materialId() != null) {
                    // 中間素材チェック
                    String id = (slotItem != null && slotItem.hasItemMeta())
                            ? slotItem.getItemMeta().getPersistentDataContainer()
                                .get(IntermediateMaterials.KEY, PersistentDataType.STRING)
                            : null;
                    if (!ing.materialId().equals(id)) return false;
                } else if (ing.vanilla() != null && ing.vanilla() != Material.AIR) {
                    // バニラ素材チェック
                    if (slotItem == null || slotItem.getType() != ing.vanilla()) return false;
                } else {
                    // 空スロット
                    if (slotItem != null && slotItem.getType() != Material.AIR) return false;
                }
            }
        }
        return true;
    }

    /**
     * レシピに従い材料を消費し、結果アイテムを返す。
     * スロット配列を直接書き換える。
     */
    public static ItemStack consumeAndGet(CustomCrafterRecipe r, ItemStack[] slots) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIdx = row * 3 + col;
                char patternChar = (row < r.shape.length && col < r.shape[row].length())
                        ? r.shape[row].charAt(col) : ' ';
                Ingredient ing = r.ingredients.getOrDefault(patternChar, Ingredient.air());
                if (ing.materialId() != null || (ing.vanilla() != null && ing.vanilla() != Material.AIR)) {
                    if (slotIdx < slots.length && slots[slotIdx] != null) {
                        int amt = slots[slotIdx].getAmount() - 1;
                        if (amt <= 0) { slots[slotIdx] = null; }
                        else { slots[slotIdx].setAmount(amt); }
                    }
                }
            }
        }
        return r.result().clone();
    }

    // ---- 全レシピ定義 -------------------------------------------------------

    public static void init() {
        // Tier 1: 回路基板
        register(new CustomCrafterRecipe(
                "circuit_board",
                new String[]{"RCR", "C C", "RCR"},
                Map.of(
                        'R', Ingredient.of(Material.REDSTONE),
                        'C', Ingredient.of(Material.COPPER_INGOT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD)
        ));

        // Tier 1: 機械歯車
        register(new CustomCrafterRecipe(
                "machine_gear",
                new String[]{" I ", "I I", " I "},
                Map.of(
                        'I', Ingredient.of(Material.IRON_NUGGET)
                ),
                IntermediateMaterials.build(IntermediateMaterials.MACHINE_GEAR)
        ));

        // Tier 1: 機械筐体
        register(new CustomCrafterRecipe(
                "machine_casing",
                new String[]{"III", "I I", "III"},
                Map.of(
                        'I', Ingredient.of(Material.IRON_INGOT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING)
        ));

        // Tier 2: 演算処理装置
        register(new CustomCrafterRecipe(
                "processor_unit",
                new String[]{"CQC", "QCQ", "CQC"},
                Map.of(
                        'C', Ingredient.ofId(IntermediateMaterials.CIRCUIT_BOARD),
                        'Q', Ingredient.of(Material.QUARTZ)
                ),
                IntermediateMaterials.build(IntermediateMaterials.PROCESSOR_UNIT)
        ));

        // Tier 2: 駆動モーター
        register(new CustomCrafterRecipe(
                "motor",
                new String[]{" G ", "GPG", " G "},
                Map.of(
                        'G', Ingredient.ofId(IntermediateMaterials.MACHINE_GEAR),
                        'P', Ingredient.of(Material.PISTON)
                ),
                IntermediateMaterials.build(IntermediateMaterials.MOTOR)
        ));

        // Tier 2: 加熱コイル
        register(new CustomCrafterRecipe(
                "heating_coil",
                new String[]{" B ", "BCB", " B "},
                Map.of(
                        'B', Ingredient.of(Material.BLAZE_POWDER),
                        'C', Ingredient.of(Material.COPPER_INGOT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL)
        ));

        // Tier 2: 冷却セル
        register(new CustomCrafterRecipe(
                "cooling_cell",
                new String[]{"CIC", "CIC", "CIC"},
                Map.of(
                        'C', Ingredient.of(Material.COPPER_INGOT),
                        'I', Ingredient.of(Material.ICE)
                ),
                IntermediateMaterials.build(IntermediateMaterials.COOLING_CELL)
        ));

        // Tier 2: 強化筐体
        register(new CustomCrafterRecipe(
                "reinforced_casing",
                new String[]{"OCO", "C C", "OCO"},
                Map.of(
                        'O', Ingredient.of(Material.OBSIDIAN),
                        'C', Ingredient.ofId(IntermediateMaterials.MACHINE_CASING)
                ),
                IntermediateMaterials.build(IntermediateMaterials.REINFORCED_CASING)
        ));

        // Tier 3: エネルギーコア
        register(new CustomCrafterRecipe(
                "energy_core",
                new String[]{"DPD", "RPR", "DPD"},
                Map.of(
                        'D', Ingredient.of(Material.DIAMOND),
                        'P', Ingredient.ofId(IntermediateMaterials.PROCESSOR_UNIT),
                        'R', Ingredient.of(Material.REDSTONE_BLOCK)
                ),
                IntermediateMaterials.build(IntermediateMaterials.ENERGY_CORE)
        ));

        // Tier 3: 安定化装置
        register(new CustomCrafterRecipe(
                "stabilizer",
                new String[]{"SAS", "ACA", "SAS"},
                Map.of(
                        'S', Ingredient.of(Material.NETHERITE_SCRAP),
                        'A', Ingredient.of(Material.AMETHYST_SHARD),
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE)
                ),
                IntermediateMaterials.build(IntermediateMaterials.STABILIZER)
        ));

        // ========== バニラ素材の一括変換レシピ ==========

        // 鉄ブロック ↔ 鉄インゴット9個相当の圧縮解除はバニラにあるので不要

        // 圧縮: インゴット9 → ブロック (バニラにあるので不要)

        // レッドストーンブロック → レッドストーン9個
        register(new CustomCrafterRecipe(
                "redstone_decompress",
                new String[]{"R", "R", "R"},
                Map.of(
                        'R', Ingredient.of(Material.REDSTONE_BLOCK)
                ),
                new ItemStack(Material.REDSTONE, 9)
        ));

        // 銅ブロック → 銅インゴット9個 (バニアにあるが成形レシピとして)
        register(new CustomCrafterRecipe(
                "copper_decompress",
                new String[]{"C", "C", "C"},
                Map.of(
                        'C', Ingredient.of(Material.COPPER_BLOCK)
                ),
                new ItemStack(Material.COPPER_INGOT, 9)
        ));

        // ========== 装備 ==========

        // ナノソード
        register(new CustomCrafterRecipe(
                "nano_sword",
                new String[]{"SPS", "SDS", " C "},
                Map.of(
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER),
                        'P', Ingredient.ofId(IntermediateMaterials.PROCESSOR_UNIT),
                        'D', Ingredient.of(Material.DIAMOND),
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.NANO_SWORD)
        ));

        // マイニングドリル
        register(new CustomCrafterRecipe(
                "mining_drill",
                new String[]{"M M", "RDR", " O "},
                Map.of(
                        'M', Ingredient.ofId(IntermediateMaterials.MOTOR),
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING),
                        'D', Ingredient.of(Material.DIAMOND_BLOCK),
                        'O', Ingredient.of(Material.OBSIDIAN)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.MINING_DRILL)
        ));

        // フォースヘルメット
        register(new CustomCrafterRecipe(
                "force_helmet",
                new String[]{"CRC", "S S", "   "},
                Map.of(
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE),
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING),
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.FORCE_HELMET)
        ));

        // フォースチェストプレート
        register(new CustomCrafterRecipe(
                "force_chestplate",
                new String[]{"R R", "SCS", "RPR"},
                Map.of(
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING),
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER),
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE),
                        'P', Ingredient.ofId(IntermediateMaterials.PROCESSOR_UNIT)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.FORCE_CHESTPLATE)
        ));

        // フォースレギンス
        register(new CustomCrafterRecipe(
                "force_leggings",
                new String[]{"SRS", "R R", "C C"},
                Map.of(
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER),
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING),
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.FORCE_LEGGINGS)
        ));

        // フォースブーツ
        register(new CustomCrafterRecipe(
                "force_boots",
                new String[]{"C C", "R R", "   "},
                Map.of(
                        'C', Ingredient.ofId(IntermediateMaterials.ENERGY_CORE),
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING)
                ),
                net.circuitsurvival.items.CustomItems.build(net.circuitsurvival.items.CustomItems.FORCE_BOOTS)
        ));

        // ========== Tier 4 素材 ==========

        // 超伝導体: STABILIZER + COPPER_INGOT + DIAMOND
        register(new CustomCrafterRecipe(
                "super_conductor",
                new String[]{"CDC", "SPS", "CDC"},
                Map.of(
                        'C', Ingredient.of(Material.COPPER_INGOT),
                        'D', Ingredient.of(Material.DIAMOND),
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER),
                        'P', Ingredient.ofId(IntermediateMaterials.PROCESSOR_UNIT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.SUPER_CONDUCTOR)
        ));

        // 虚空結晶: SUPER_CONDUCTOR + ENDER_PEARL + NETHER_STAR
        register(new CustomCrafterRecipe(
                "void_crystal",
                new String[]{"ESE", "SNS", "ESE"},
                Map.of(
                        'E', Ingredient.of(Material.ENDER_PEARL),
                        'S', Ingredient.ofId(IntermediateMaterials.SUPER_CONDUCTOR),
                        'N', Ingredient.of(Material.NETHER_STAR)
                ),
                IntermediateMaterials.build(IntermediateMaterials.VOID_CRYSTAL)
        ));

        // ========== Tier 4+ 中間素材 ==========

        // 高度回路基板: PROCESSOR_UNIT + REDSTONE_BLOCK + GOLD_INGOT
        register(new CustomCrafterRecipe(
                "advanced_circuit",
                new String[]{"PPP", "RGR", "PPP"},
                Map.of(
                        'P', Ingredient.ofId(IntermediateMaterials.PROCESSOR_UNIT),
                        'R', Ingredient.of(Material.REDSTONE_BLOCK),
                        'G', Ingredient.of(Material.GOLD_INGOT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.ADVANCED_CIRCUIT)
        ));

        // 高級歯車: MACHINE_GEAR + MOTOR + IRON_BLOCK
        register(new CustomCrafterRecipe(
                "high_gear",
                new String[]{"GMG", "MIM", "GMG"},
                Map.of(
                        'G', Ingredient.ofId(IntermediateMaterials.MACHINE_GEAR),
                        'M', Ingredient.ofId(IntermediateMaterials.MOTOR),
                        'I', Ingredient.of(Material.IRON_BLOCK)
                ),
                IntermediateMaterials.build(IntermediateMaterials.HIGH_GEAR)
        ));

        // 量子チップ: SUPER_CONDUCTOR + ADVANCED_CIRCUIT + NETHERITE_INGOT
        register(new CustomCrafterRecipe(
                "quantum_chip",
                new String[]{"SCS", "CAC", "SCS"},
                Map.of(
                        'S', Ingredient.ofId(IntermediateMaterials.SUPER_CONDUCTOR),
                        'C', Ingredient.ofId(IntermediateMaterials.ADVANCED_CIRCUIT),
                        'A', Ingredient.of(Material.NETHERITE_INGOT)
                ),
                IntermediateMaterials.build(IntermediateMaterials.QUANTUM_CHIP)
        ));

        // 中性子反射材: REINFORCED_CASING + OBSIDIAN + STABILIZER
        register(new CustomCrafterRecipe(
                "neutron_reflector",
                new String[]{"ROR", "OSO", "ROR"},
                Map.of(
                        'R', Ingredient.ofId(IntermediateMaterials.REINFORCED_CASING),
                        'O', Ingredient.of(Material.OBSIDIAN),
                        'S', Ingredient.ofId(IntermediateMaterials.STABILIZER)
                ),
                IntermediateMaterials.build(IntermediateMaterials.NEUTRON_REFLECTOR)
        ));

        // ========== Tier 4 中間素材 ==========

        // 分子回路: QUANTUM_CHIP + DIAMOND_BLOCK + NETHER_STAR
        register(new CustomCrafterRecipe(
                "molecular_circuit",
                new String[]{"QDQ", "DND", "QDQ"},
                Map.of(
                        'Q', Ingredient.ofId(IntermediateMaterials.QUANTUM_CHIP),
                        'D', Ingredient.of(Material.DIAMOND_BLOCK),
                        'N', Ingredient.of(Material.NETHER_STAR)
                ),
                IntermediateMaterials.build(IntermediateMaterials.MOLECULAR_CIRCUIT)
        ));

        // 冷却剤セル: COOLING_CELL + BLUE_ICE + DIAMOND
        register(new CustomCrafterRecipe(
                "coolant_cell",
                new String[]{"CBC", "BDB", "CBC"},
                Map.of(
                        'C', Ingredient.ofId(IntermediateMaterials.COOLING_CELL),
                        'B', Ingredient.of(Material.BLUE_ICE),
                        'D', Ingredient.of(Material.DIAMOND)
                ),
                IntermediateMaterials.build(IntermediateMaterials.COOLANT_CELL)
        ));

        // ========== バッテリー ==========
        // 回路基板+レッドストーン+銅で乾電池を作る
        ItemStack battery = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta bm = battery.getItemMeta();
        bm.displayName(net.kyori.adventure.text.Component.text("乾電池", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        bm.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7ENを保管する携帯バッテリー"),
                net.kyori.adventure.text.Component.text("§7充電器で充電 / 機械に右クリックで放電"),
                net.kyori.adventure.text.Component.text("§eEN: 0 / 10000")
        ));
        bm.getPersistentDataContainer().set(NamespacedKey.fromString("circuitsurvival:en_storage"), PersistentDataType.INTEGER, 0);
        battery.setItemMeta(bm);

        register(new CustomCrafterRecipe(
                "battery",
                new String[]{"CRC", "R R", "CRC"},
                Map.of(
                        'C', Ingredient.ofId(IntermediateMaterials.CIRCUIT_BOARD),
                        'R', Ingredient.of(Material.REDSTONE)
                ),
                battery
        ));
    }
}
