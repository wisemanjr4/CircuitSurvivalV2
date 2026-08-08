package net.circuitsurvival.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class IntermediateMaterials {

    public static NamespacedKey KEY;

    // Chain 1 - ベーシック中間素材 (CRUSHER/COMPRESSOR/AUTO_SMELTER)
    public static final String CRUSHED_IRON = "crushed_iron";
    public static final String CRUSHED_GOLD = "crushed_gold";
    public static final String CRUSHED_COAL = "crushed_coal";
    public static final String CRUSHED_STONE = "crushed_stone";
    public static final String CRUSHED_NETHERRACK = "crushed_netherrack";
    public static final String REFINED_IRON_INGOT = "refined_iron_ingot";
    public static final String REFINED_GOLD_INGOT = "refined_gold_ingot";
    public static final String COMPRESSED_IRON = "compressed_iron";
    public static final String COMPRESSED_GOLD = "compressed_gold";

    // Tier 1 - 基本素材
    public static final String CIRCUIT_BOARD = "circuit_board";
    public static final String MACHINE_GEAR = "machine_gear";
    public static final String MACHINE_CASING = "machine_casing";

    // Tier 2 - 応用部品
    public static final String PROCESSOR_UNIT = "processor_unit";
    public static final String MOTOR = "motor";
    public static final String HEATING_COIL = "heating_coil";
    public static final String COOLING_CELL = "cooling_cell";
    public static final String REINFORCED_CASING = "reinforced_casing";

    // Tier 2.5 - 高度部品
    public static final String ADVANCED_CIRCUIT = "advanced_circuit";
    public static final String HIGH_GEAR = "high_gear";

    // Tier 3 - 先進部品
    public static final String ENERGY_CORE = "energy_core";
    public static final String STABILIZER = "stabilizer";

    // Tier 3.5 - 超先進部品
    public static final String QUANTUM_CHIP = "quantum_chip";
    public static final String NEUTRON_REFLECTOR = "neutron_reflector";

    // Tier 4 - 究極素材
    public static final String MOLECULAR_CIRCUIT = "molecular_circuit";
    public static final String COOLANT_CELL = "coolant_cell";

    // Tier 4 - エンドゲーム
    public static final String SUPER_CONDUCTOR = "super_conductor";
    public static final String VOID_CRYSTAL = "void_crystal";

    // Chain 3 - 機械部品製造 (Phase 2)
    public static final String PURE_IRON_EXTRACT = "pure_iron_extract";
    public static final String PURE_GOLD_EXTRACT = "pure_gold_extract";
    public static final String STEEL_INGOT = "steel_ingot";
    public static final String ADVANCED_ALLOY = "advanced_alloy";
    public static final String EN_CELL_COMPONENT = "en_cell_component";
    public static final String PIPE_CONNECTOR = "pipe_connector";

    // Chain 4 - 化学処理 (Phase 5)
    public static final String CRUDE_OIL = "crude_oil";
    public static final String CHEMICAL_OIL = "chemical_oil";
    public static final String RUBBER = "rubber";
    public static final String PLASTIC = "plastic";
    public static final String SULFURIC_ACID = "sulfuric_acid";
    public static final String SULFUR = "sulfur";

    // Chain 5 - 高度冶金 (Phase 6)
    public static final String HARDENED_ALLOY = "hardened_alloy";
    public static final String INDUSTRIAL_DIAMOND = "industrial_diamond";
    public static final String HEAT_RESISTANT_CERAMIC = "heat_resistant_ceramic";
    public static final String REINFORCED_PLATE = "reinforced_plate";

    public static ItemStack build(String id) { return build(id, 1); }

    public static ItemStack build(String id, int amount) {
        ItemStack base = buildSingle(id);
        if (base != null) { base.setAmount(amount); return base; }
        return null;
    }

    private static ItemStack buildSingle(String id) {
        return switch (id) {
            // Chain 1 - ベーシック中間素材
            case CRUSHED_IRON -> make(Material.IRON_NUGGET, 1, id,
                    "粉砕鉄鉱石", NamedTextColor.WHITE,
                    "§7鉄鉱石を粉砕した粉末。精錬すると純度の高い鉄になる。",
                    "§8CRUSHER - 自動化 Chain 1");
            case CRUSHED_GOLD -> make(Material.GOLD_NUGGET, 1, id,
                    "粉砕金鉱石", NamedTextColor.GOLD,
                    "§7金鉱石を粉砕した粉末。",
                    "§8CRUSHER - 自動化 Chain 1");
            case CRUSHED_COAL -> make(Material.BLACK_DYE, 1, id,
                    "粉砕石炭", NamedTextColor.DARK_GRAY,
                    "§7石炭を粉砕した粉末。燃料として高効率。",
                    "§8CRUSHER - 自動化 Chain 1");
            case CRUSHED_STONE -> make(Material.GRAVEL, 1, id,
                    "粉砕石材", NamedTextColor.GRAY,
                    "§7丸石を粉砕した砂利状の建材。",
                    "§8CRUSHER - 自動化 Chain 1");
            case CRUSHED_NETHERRACK -> make(Material.NETHERRACK, 1, id,
                    "粉砕ネザーラック", NamedTextColor.DARK_RED,
                    "§7ネザーラックを粉砕したもの。",
                    "§8CRUSHER - 自動化 Chain 1");
            case REFINED_IRON_INGOT -> make(Material.IRON_INGOT, 1, id,
                    "精錬鉄インゴット", NamedTextColor.WHITE,
                    "§7粉砕鉄鉱石を精錬した高純度の鉄インゴット。",
                    "§8AUTO_SMELTER - 自動化 Chain 1");
            case REFINED_GOLD_INGOT -> make(Material.GOLD_INGOT, 1, id,
                    "精錬金インゴット", NamedTextColor.GOLD,
                    "§7粉砕金鉱石を精錬した高純度の金インゴット。",
                    "§8AUTO_SMELTER - 自動化 Chain 1");
            case COMPRESSED_IRON -> make(Material.IRON_BLOCK, 1, id,
                    "圧縮鉄ブロック", NamedTextColor.WHITE,
                    "§7鉄インゴット9個を圧縮した強化ブロック。",
                    "§8COMPRESSOR - 自動化 Chain 1");
            case COMPRESSED_GOLD -> make(Material.GOLD_BLOCK, 1, id,
                    "圧縮金ブロック", NamedTextColor.GOLD,
                    "§7金インゴット9個を圧縮した強化ブロック。",
                    "§8COMPRESSOR - 自動化 Chain 1");
            // Tier 1
            case CIRCUIT_BOARD -> make(Material.GOLD_NUGGET, 1, id,
                    "回路基板", NamedTextColor.GOLD,
                    "§7銅とレッドストーンを焼き付けた基礎回路基板",
                    "§8Tier 1 - 基本素材");
            case MACHINE_GEAR -> make(Material.IRON_NUGGET, 1, id,
                    "機械歯車", NamedTextColor.WHITE,
                    "§7精密に削り出された鉄製の歯車",
                    "§8Tier 1 - 基本素材");
            case MACHINE_CASING -> make(Material.IRON_BLOCK, 1, id,
                    "機械筐体", NamedTextColor.GRAY,
                    "§7機械の外枠となる頑丈な鉄製筐体",
                    "§8Tier 1 - 基本素材");
            // Tier 2
            case PROCESSOR_UNIT -> make(Material.GOLD_INGOT, 1, id,
                    "演算処理装置", NamedTextColor.YELLOW,
                    "§b回路基板§7に§b水晶§7と§b金§7を集積した処理ユニット",
                    "§8Tier 2 - 応用部品");
            case MOTOR -> make(Material.PISTON, 1, id,
                    "駆動モーター", NamedTextColor.DARK_RED,
                    "§b歯車§7と§bピストン§7を組み合わせた動力源",
                    "§8Tier 2 - 応用部品");
            case HEATING_COIL -> make(Material.COPPER_INGOT, 1, id,
                    "加熱コイル", NamedTextColor.RED,
                    "§b銅線§7に§bブレイズパウダー§7を溶浸した発熱素子",
                    "§8Tier 2 - 応用部品");
            case COOLING_CELL -> make(Material.PACKED_ICE, 1, id,
                    "冷却セル", NamedTextColor.AQUA,
                    "§b銅§7と§b氷§7で構成された冷却モジュール",
                    "§8Tier 2 - 応用部品");
            case REINFORCED_CASING -> make(Material.OBSIDIAN, 1, id,
                    "強化筐体", NamedTextColor.DARK_GRAY,
                    "§b機械筐体§7に§b黒曜石§7を装甲した強化外骨格",
                    "§8Tier 2 - 応用部品");
            // Tier 2.5
            case ADVANCED_CIRCUIT -> make(Material.REPEATER, 1, id,
                    "高度回路基板", NamedTextColor.GOLD,
                    "§b演算処理装置§7に§bRSブロック§7と§b金§7を高密度集積",
                    "§8Tier 2.5 - 高度部品");
            case HIGH_GEAR -> make(Material.IRON_BLOCK, 1, id,
                    "高級歯車", NamedTextColor.WHITE,
                    "§b機械歯車§7に§b鉄ブロック§7を融合した超精密駆動歯車",
                    "§8Tier 2.5 - 高度部品");
            // Tier 3
            case ENERGY_CORE -> make(Material.REDSTONE_BLOCK, 1, id,
                    "エネルギーコア", NamedTextColor.DARK_RED,
                    "§b演算処理装置§7に§bRSブロック§7と§bダイヤ§7で増幅した高出力電源",
                    "§8Tier 3 - 先進部品");
            case STABILIZER -> make(Material.DIAMOND, 1, id,
                    "安定化装置", NamedTextColor.DARK_PURPLE,
                    "§bエネルギーコア§7を§bネザースクラップ§7と§bアメジスト§7で安定化",
                    "§8Tier 3 - 先進部品");
            // Tier 3.5
            case QUANTUM_CHIP -> make(Material.ECHO_SHARD, 1, id,
                    "量子チップ", NamedTextColor.LIGHT_PURPLE,
                    "§b高度回路§7に§b超伝導体§7と§bネザライト§7を量子集積",
                    "§8Tier 3.5 - 超先進部品");
            case NEUTRON_REFLECTOR -> make(Material.NETHER_BRICKS, 1, id,
                    "中性子反射材", NamedTextColor.DARK_RED,
                    "§b強化筐体§7に§b黒曜石§7と§b安定化装置§7で中性子を閉じ込め",
                    "§8Tier 3.5 - 超先進部品");
            // Tier 4
            case MOLECULAR_CIRCUIT -> make(Material.NETHERITE_SCRAP, 1, id,
                    "分子回路", NamedTextColor.DARK_PURPLE,
                    "§b量子チップ§7に§bダイヤモンド§7と§bネザースター§7で物質を制御",
                    "§8Tier 4 - 究極素材");
            case COOLANT_CELL -> make(Material.BLUE_ICE, 1, id,
                    "冷却剤セル", NamedTextColor.AQUA,
                    "§b冷却セル§7を§b青氷§7と§bダイヤ§7で超冷却した冷却剤",
                    "§8Tier 4 - 究極素材");
            // Tier 4
            case SUPER_CONDUCTOR -> make(Material.LIGHT_BLUE_DYE, 1, id,
                    "超伝導体", NamedTextColor.AQUA,
                    "§b安定化装置§7に§b銅§7と§bダイヤ§7を超圧縮した次世代導体",
                    "§8Tier 4 - エンドゲーム");
            case VOID_CRYSTAL -> make(Material.ENDER_EYE, 1, id,
                    "虚空結晶", NamedTextColor.DARK_PURPLE,
                    "§b超伝導体§7に§bエンダーパール§7と§bネザースター§7を融合",
                    "§8Tier 4 - エンドゲーム");
            // Chain 3 - 機械部品製造 (Phase 2)
            case PURE_IRON_EXTRACT -> make(Material.GRAY_DYE, 1, id,
                    "純鉄抽出液", NamedTextColor.WHITE,
                    "§7粉砕鉄鉱石を遠心分離して得た高純度の鉄エキス。合金の原料。",
                    "§8CENTRIFUGE - Chain 3");
            case PURE_GOLD_EXTRACT -> make(Material.ORANGE_DYE, 1, id,
                    "純金抽出液", NamedTextColor.GOLD,
                    "§7粉砕金鉱石を遠心分離して得た高純度の金エキス。",
                    "§8CENTRIFUGE - Chain 3");
            case STEEL_INGOT -> make(Material.IRON_INGOT, 1, id,
                    "鋼鉄インゴット", NamedTextColor.GRAY,
                    "§7精錬鉄と粉砕石炭を高温溶解した合金。強度と耐久性が高い。",
                    "§8INDUCTION_FURNACE - Chain 3");
            case ADVANCED_ALLOY -> make(Material.NETHERITE_SCRAP, 1, id,
                    "先進合金", NamedTextColor.DARK_PURPLE,
                    "§7鋼鉄と純鉄抽出液を融合した超硬合金。",
                    "§8INDUCTION_FURNACE - Chain 3");
            case EN_CELL_COMPONENT -> make(Material.LAPIS_LAZULI, 1, id,
                    "ENセル部品", NamedTextColor.BLUE,
                    "§7蓄電機から取り出したエネルギー制御基盤。",
                    "§8Tier 4 - 動力部品");
            case PIPE_CONNECTOR -> make(Material.CHAIN, 1, id,
                    "パイプコネクタ", NamedTextColor.AQUA,
                    "§7鋼鉄とレッドストーンで作られた配管接続部品。",
                    "§8Tier 3 - 物流部品");
            // Chain 4 - 化学処理 (Phase 5)
            case CRUDE_OIL -> make(Material.SLIME_BALL, 1, id,
                    "原油", NamedTextColor.DARK_GREEN,
                    "§7地中深くから採掘される黒い液体。蒸留で様々な化学製品になる。",
                    "§8採掘 - Chain 4");
            case CHEMICAL_OIL -> make(Material.GLOW_INK_SAC, 1, id,
                    "ケミカルオイル", NamedTextColor.AQUA,
                    "§7原油を蒸留して得た精製油。化学反応の基本素材。",
                    "§8DISTILLATION_TOWER - Chain 4");
            case RUBBER -> make(Material.RABBIT_HIDE, 1, id,
                    "ゴム", NamedTextColor.DARK_GRAY,
                    "§7原油蒸留の副産物。弾力性と絶縁性に優れる。",
                    "§8DISTILLATION_TOWER - Chain 4");
            case PLASTIC -> make(Material.WHITE_DYE, 1, id,
                    "プラスチック", NamedTextColor.WHITE,
                    "§7ゴムと硫黄を化学反応させて作る合成樹脂。",
                    "§8CHEMICAL_REACTOR - Chain 4");
            case SULFURIC_ACID -> make(Material.LIME_DYE, 1, id,
                    "硫酸", NamedTextColor.GREEN,
                    "§7化学反応炉でケミカルオイルから合成。強力な触媒。",
                    "§8CHEMICAL_REACTOR - Chain 4");
            case SULFUR -> make(Material.YELLOW_DYE, 1, id,
                    "硫黄", NamedTextColor.YELLOW,
                    "§7原油蒸留で得られる黄色い粉末。ゴムの加硫に必須。",
                    "§8DISTILLATION_TOWER - Chain 4");
            // Chain 5 - 高度冶金 (Phase 6)
            case HARDENED_ALLOY -> make(Material.GOLD_BLOCK, 1, id,
                    "超硬合金", NamedTextColor.GOLD,
                    "§7先進合金を真空溶解で純化した最強の合金。",
                    "§8VACUUM_FURNACE - Chain 5");
            case INDUSTRIAL_DIAMOND -> make(Material.DIAMOND, 1, id,
                    "工業用ダイヤ", NamedTextColor.AQUA,
                    "§7石炭を超高圧で合成した人工ダイヤモンド。",
                    "§8HIGH_PRESSURE_PRESS - Chain 5");
            case HEAT_RESISTANT_CERAMIC -> make(Material.BRICK, 1, id,
                    "耐熱セラミック", NamedTextColor.RED,
                    "§7粘土と硫黄を化学処理した耐熱素材。",
                    "§8CHEMICAL_REACTOR - Chain 5");
            case REINFORCED_PLATE -> make(Material.IRON_TRAPDOOR, 1, id,
                    "強化プレート", NamedTextColor.WHITE,
                    "§7超硬合金×2+プラスチックで成形した装甲板。",
                    "§8CRAFTING - Chain 5");
            default -> null;
        };
    }

    public static String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack item, String id) {
        return id.equals(getId(item));
    }

    private static ItemStack make(Material mat, int amount, String id,
                                  String name, NamedTextColor color, String... lore) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore)
                .map(l -> (Component) Component.text(l, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        is.setItemMeta(meta);
        return is;
    }
}
