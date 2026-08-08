package net.circuitsurvival.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * 料理 / PixelGun 元ネタカスタムアイテムの定義と生成
 *
 * 使用前に CircuitSurvivalPlugin.onEnable() で KEY / PROJECTILE_KEY を初期化すること。
 */
public class CustomItems {

    // ---- PDCキー (プラグイン起動時に初期化) -----------------------------------
    /** アイテムID識別キー */
    public static NamespacedKey KEY;
    /** 発射物追跡キー (飛翔エンティティに付与) */
    public static NamespacedKey PROJECTILE_KEY;

    // ---- アイテムID定数 -------------------------------------------------------
    public static final String EXPLOSIVE_CROQUETTE = "explosive_croquette";
    public static final String CHEF_KNIFE          = "chef_knife";
    public static final String SPICE_CURRY         = "spice_curry";
    public static final String PIXEL_GUN           = "pixel_gun";
    public static final String DARK_SABER          = "dark_saber";
    public static final String GRENADE_LAUNCHER    = "grenade_launcher";
    public static final String SNIPER_RIFLE        = "sniper_rifle";
    // 装備
    public static final String NANO_SWORD      = "nano_sword";
    public static final String MINING_DRILL    = "mining_drill";
    public static final String FORCE_HELMET    = "force_helmet";
    public static final String FORCE_CHESTPLATE = "force_chestplate";
    public static final String FORCE_LEGGINGS  = "force_leggings";
    public static final String FORCE_BOOTS     = "force_boots";

    // ---- ファクトリ -----------------------------------------------------------

    /** アイテムIDからItemStackを生成する */
    public static ItemStack build(String id) {
        return switch (id) {
            case EXPLOSIVE_CROQUETTE -> make(Material.FIRE_CHARGE, 4, id,
                    "爆発コロッケ", NamedTextColor.GOLD, false,
                    "料理界最強の投擲兵器。右クリックで投げて爆発させよう。",
                    "威力: 中 (Power 2) / 火災: なし");
            case CHEF_KNIFE -> make(Material.IRON_SWORD, 1, id,
                    "シェフナイフ", NamedTextColor.YELLOW, false,
                    "一流シェフの包丁。命中時にPoison+Slowness付与。",
                    "命中: Poison I (2s) + Slowness II (3s)");
            case SPICE_CURRY -> make(Material.RABBIT_STEW, 4, id,
                    "特製スパイスカレー", NamedTextColor.RED, false,
                    "超辛口カレー。食べると短時間だけ戦士モードに突入。",
                    "効果: Speed III + Strength II + 耐火 (20s)");
            case PIXEL_GUN -> make(Material.CROSSBOW, 1, id,
                    "ピクセルガン", NamedTextColor.AQUA, true,
                    "ドット絵の銃。命中した相手が10秒間発光する。",
                    "命中: Glowing 10s");
            case DARK_SABER -> make(Material.NETHERITE_SWORD, 1, id,
                    "ダークセイバー", NamedTextColor.DARK_PURPLE, true,
                    "闇の力を宿した剣。命中でウィザー+失明を付与。",
                    "命中: Wither II (5s) + Blindness (4s)");
            case GRENADE_LAUNCHER -> make(Material.BOW, 1, id,
                    "グレネードランチャー", NamedTextColor.GREEN, false,
                    "見た目は弓、実はグレネード発射装置。",
                    "着弾: 爆発 威力3 / 火災: なし");
            case SNIPER_RIFLE -> make(Material.CROSSBOW, 1, id,
                    "スナイパーライフル", NamedTextColor.WHITE, true,
                    "高威力狙撃銃。発射時にNight Visionを付与。",
                    "命中時ボーナスダメージ: +20 / 発射時: Night Vision 3s");
            // 装備
            case NANO_SWORD -> {
                ItemStack is = make(Material.NETHERITE_SWORD, 1, id,
                        "ナノソード", NamedTextColor.AQUA, true,
                        "ナノテクノロジーで強化された最強の近接武器",
                        "ダメージ: +14 / 命中時ウィザー付与");
                is.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 7);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 5);
                yield is;
            }
            case MINING_DRILL -> {
                ItemStack is = make(Material.DIAMOND_PICKAXE, 1, id,
                        "マイニングドリル", NamedTextColor.GOLD, true,
                        "高出力モーター駆動の自動採掘ドリル",
                        "採掘速度: 効率強化VII / 耐性: 耐久V");
                is.addUnsafeEnchantment(Enchantment.DIG_SPEED, 7);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 5);
                yield is;
            }
            case FORCE_HELMET -> {
                ItemStack is = make(Material.NETHERITE_HELMET, 1, id,
                        "フォースヘルメット", NamedTextColor.DARK_RED, true,
                        "フォースアーマーのヘルメット",
                        "ダメージ軽減IV / 耐久IV");
                is.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 4);
                yield is;
            }
            case FORCE_CHESTPLATE -> {
                ItemStack is = make(Material.NETHERITE_CHESTPLATE, 1, id,
                        "フォースチェストプレート", NamedTextColor.DARK_RED, true,
                        "フォースアーマーのチェストプレート",
                        "ダメージ軽減IV / 耐久IV");
                is.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 4);
                yield is;
            }
            case FORCE_LEGGINGS -> {
                ItemStack is = make(Material.NETHERITE_LEGGINGS, 1, id,
                        "フォースレギンス", NamedTextColor.DARK_RED, true,
                        "フォースアーマーのレギンス",
                        "ダメージ軽減IV / 耐久IV");
                is.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 4);
                yield is;
            }
            case FORCE_BOOTS -> {
                ItemStack is = make(Material.NETHERITE_BOOTS, 1, id,
                        "フォースブーツ", NamedTextColor.DARK_RED, true,
                        "フォースアーマーのブーツ",
                        "ダメージ軽減IV / 耐久IV");
                is.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                is.addUnsafeEnchantment(Enchantment.DURABILITY, 4);
                yield is;
            }
            default -> null;
        };
    }

    // ---- ヘルパー -------------------------------------------------------------

    private static ItemStack make(Material mat, int amount, String id,
                                  String name, NamedTextColor color, boolean glow,
                                  String... lore) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(Arrays.stream(lore)
                .map(l -> (Component) Component.text(l, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        is.setItemMeta(meta);
        return is;
    }

    /** アイテムが指定のカスタムIDか確認する */
    public static boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(KEY, PersistentDataType.STRING);
        return id.equals(val);
    }

    /** アイテムのカスタムIDを返す (カスタムアイテムでなければnull) */
    public static String getId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY, PersistentDataType.STRING);
    }
}
