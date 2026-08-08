package net.circuitsurvival.recipes;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/**
 * 入手強化レシピ集
 *
 * 方針:
 * - 序盤から使えるありふれた素材で代替可能に
 * - バニラより効率が良すぎないバランス
 * - 一貫した素材価値の換算
 * - 無限ループや不当な増殖を防ぐ
 */
public class CustomRecipes {

    private final Plugin plugin;

    public CustomRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        registerStringRecipes();
        registerIronRecipes();
        registerLeatherRecipes();
        registerGunpowderRecipes();
        registerSlimeRecipes();
        registerBlazeRecipes();
        registerNetherMaterials();
        registerMiscSurvivalRecipes();
        registerCircuitComponents();
    }

    // =====================================================================
    // 糸 (String)
    // =====================================================================
    private void registerStringRecipes() {
        add(shapeless("cobweb_to_string",  new ItemStack(Material.STRING, 9),  Material.COBWEB));
        add(shapeless("vine_to_string",    new ItemStack(Material.STRING, 1),  Material.VINE, Material.VINE));
        add(shapeless("bamboo_to_string",  new ItemStack(Material.STRING, 2),  Material.BAMBOO, Material.BAMBOO, Material.BAMBOO));
        add(shapeless("wool_to_string",    new ItemStack(Material.STRING, 4),  Material.WHITE_WOOL));
        add(shapeless("deadbush_to_string",new ItemStack(Material.STRING, 1),  Material.DEAD_BUSH));
        add(shapeless("leaves_to_string",  new ItemStack(Material.STRING, 2),
                Material.OAK_LEAVES, Material.OAK_LEAVES,
                Material.OAK_LEAVES, Material.OAK_LEAVES));
    }

    // =====================================================================
    // 鉄 (Iron)
    // =====================================================================
    private void registerIronRecipes() {
        // 鉄格子 3本 → 鉄インゴット 1個 (バニア: 6インゴット→16格子、3格子→1は0.333/本で損失気味)
        add(shapeless("iron_bars_to_ingot", new ItemStack(Material.IRON_INGOT, 1),
                Material.IRON_BARS, Material.IRON_BARS, Material.IRON_BARS));
        // チェーン 1個 → 鉄ナゲット 6個 (中身: 鉄ナゲット2 + 鉄格子1 = 2+4=6)
        add(shapeless("chain_to_nugget",    new ItemStack(Material.IRON_NUGGET, 6), Material.CHAIN));
        // 鉄馬鎧 → 鉄インゴット 7個
        add(shapeless("iron_horse_armor_to_ingot", new ItemStack(Material.IRON_INGOT, 7), Material.IRON_HORSE_ARMOR));
    }

    // =====================================================================
    // 革 (Leather)
    // =====================================================================
    private void registerLeatherRecipes() {
        add(shapeless("rotten_flesh_to_leather", new ItemStack(Material.LEATHER, 1),
                Material.ROTTEN_FLESH, Material.ROTTEN_FLESH));
        add(shapeless("cactus_to_leather", new ItemStack(Material.LEATHER, 1),
                Material.CACTUS, Material.CACTUS));
        // きのこシチュー 1個 → 革 1個（有機廃棄物活用）
        add(shapeless("stew_to_leather", new ItemStack(Material.LEATHER, 1), Material.MUSHROOM_STEW));
    }

    // =====================================================================
    // 火薬 (Gunpowder)
    // =====================================================================
    private void registerGunpowderRecipes() {
        // 火打ち石 + 木炭 + 砂 → 火薬 2個 (シンプル形状)
        add(new ShapedRecipe(key("gunpowder_basic"), new ItemStack(Material.GUNPOWDER, 2))
                .shape("FC", " S")
                .setIngredient('F', Material.FLINT)
                .setIngredient('C', Material.CHARCOAL)
                .setIngredient('S', Material.SAND));
        // マグマブロック + 砂 → 火薬 4個
        add(shapeless("gunpowder_magma", new ItemStack(Material.GUNPOWDER, 4),
                Material.MAGMA_BLOCK, Material.SAND));
        // ネザーラック + 木炭 → 火薬 2個
        add(shapeless("gunpowder_netherrack", new ItemStack(Material.GUNPOWDER, 2),
                Material.NETHERRACK, Material.CHARCOAL));
    }

    // =====================================================================
    // スライムボール
    // =====================================================================
    private void registerSlimeRecipes() {
        add(shapeless("slime_block_decompose", new ItemStack(Material.SLIME_BALL, 9), Material.SLIME_BLOCK));
        add(shapeless("slimeball_cactus", new ItemStack(Material.SLIME_BALL, 2),
                Material.CACTUS, Material.SUGAR));
        add(shapeless("slimeball_kelp", new ItemStack(Material.SLIME_BALL, 1),
                Material.DRIED_KELP, Material.SUGAR, Material.SUGAR));
    }

    // =====================================================================
    // ブレイズパウダー / ネザー素材
    // =====================================================================
    private void registerBlazeRecipes() {
        add(shapeless("blaze_rod_to_powder_x6", new ItemStack(Material.BLAZE_POWDER, 6), Material.BLAZE_ROD));
        add(shapeless("blaze_from_netherrack", new ItemStack(Material.BLAZE_POWDER, 1),
                Material.NETHERRACK, Material.NETHERRACK, Material.NETHERRACK, Material.NETHERRACK));
    }

    // =====================================================================
    // ネザー・光源系素材
    // =====================================================================
    private void registerNetherMaterials() {
        // ネザーラック 4個 + 溶岩バケツ → クォーツ 8個
        add(new ShapedRecipe(key("quartz_from_netherrack"), new ItemStack( Material.QUARTZ, 8))
                .shape("NNN", "NLN", "NNN")
                .setIngredient('N', Material.NETHERRACK)
                .setIngredient('L', Material.LAVA_BUCKET));
        // 黄色の染料 2個 + 砂 → グロウストーンダスト 2個
        add(shapeless("glowstone_dust_dye", new ItemStack(Material.GLOWSTONE_DUST, 2),
                Material.YELLOW_DYE, Material.YELLOW_DYE, Material.SAND));
        // グロウストーンダスト 4個 → グロウストーンブロック
        add(new ShapedRecipe(key("glowstone_from_dust"), new ItemStack(Material.GLOWSTONE))
                .shape("GG", "GG").setIngredient('G', Material.GLOWSTONE_DUST));
        // マグマブロック → マグマクリーム 4個（分解）
        add(shapeless("magma_block_to_cream", new ItemStack(Material.MAGMA_CREAM, 4), Material.MAGMA_BLOCK));
    }

    // =====================================================================
    // その他入手が面倒な素材
    // =====================================================================
    private void registerMiscSurvivalRecipes() {
        // 砂利 2個 → 火打ち石 1個
        add(shapeless("gravel_to_flint", new ItemStack(Material.FLINT, 1), Material.GRAVEL, Material.GRAVEL));
        // 骨 1本 → 骨粉 4個（バニラより多め）
        add(shapeless("bone_to_meal_x4", new ItemStack(Material.BONE_MEAL, 4), Material.BONE));
        // 骨ブロック → 骨粉 9個（9→9でループ防止, 増殖防止）
        add(shapeless("bone_block_to_meal", new ItemStack(Material.BONE_MEAL, 9), Material.BONE_BLOCK));
        // 腐った肉 2個 → 骨 1本
        add(shapeless("flesh_to_bone", new ItemStack(Material.BONE, 1), Material.ROTTEN_FLESH, Material.ROTTEN_FLESH));
        // エンダーパール: クォーツ2 + ブレイズパウダー2 + 黒曜石 → 1個
        add(new ShapedRecipe(key("ender_pearl_alt"), new ItemStack(Material.ENDER_PEARL, 1))
                .shape("QB", "BO")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('B', Material.BLAZE_POWDER)
                .setIngredient('O', Material.OBSIDIAN));
        // ファントムの薄膜: スライムボール + 蜘蛛の目 + 糸 → 1枚
        add(shapeless("phantom_membrane_alt", new ItemStack(Material.PHANTOM_MEMBRANE, 1),
                Material.SLIME_BALL, Material.SPIDER_EYE, Material.STRING));
        // 羽: 鶏肉 1個 → 羽 2本
        add(shapeless("chicken_to_feather", new ItemStack(Material.FEATHER, 2), Material.CHICKEN));
        // 砂岩 → 砂 4個（分解）
        add(shapeless("sandstone_to_sand", new ItemStack(Material.SAND, 4), Material.SANDSTONE));
        // プリズマリンの欠片: クォーツ + ラピスラズリ → 2個
        add(shapeless("prismarine_shard_alt", new ItemStack(Material.PRISMARINE_SHARD, 2),
                Material.QUARTZ, Material.LAPIS_LAZULI));
        // 砂糖: サトウキビ 1本 → 砂糖 2個（バニラは1本→1個）
        add(shapeless("sugarcane_to_sugar_x2", new ItemStack(Material.SUGAR, 2), Material.SUGAR_CANE));
        // 鉄ナゲット 9個 → 鉄インゴット 1個（バニラクラフトの逆）
        add(new ShapedRecipe(key("nugget_to_ingot"), new ItemStack(Material.IRON_INGOT, 1))
                .shape("NNN", "NNN", "NNN")
                .setIngredient('N', Material.IRON_NUGGET));
    }

    // =====================================================================
    // 回路コンポーネント簡易化
    // =====================================================================
    private void registerCircuitComponents() {
        // オブザーバー 2個
        add(new ShapedRecipe(key("observer_x2"), new ItemStack(Material.OBSERVER, 2))
                .shape("RRR", "QSQ", "CCC")
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('S', Material.STONE)
                .setIngredient('C', Material.COBBLESTONE));
        // コンパレーター 2個
        add(new ShapedRecipe(key("comparator_x2"), new ItemStack(Material.COMPARATOR, 2))
                .shape("TQT", "SSS")
                .setIngredient('T', Material.REDSTONE_TORCH)
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('S', Material.STONE));
        // リピーター 2個
        add(new ShapedRecipe(key("repeater_x2"), new ItemStack(Material.REPEATER, 2))
                .shape("RTR", "SSS")
                .setIngredient('T', Material.REDSTONE_TORCH)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('S', Material.STONE));
        // レッドストーントーチ 4個
        add(shapeless("rs_torch_x4", new ItemStack(Material.REDSTONE_TORCH, 4), Material.STICK, Material.REDSTONE));
        // レッドストーンランプ 3個
        add(new ShapedRecipe(key("rs_lamp_x3"), new ItemStack(Material.REDSTONE_LAMP, 3))
                .shape("RGR", "GGG", "RGR")
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('G', Material.GLOWSTONE_DUST));
        // ドロッパー 2個
        add(new ShapedRecipe(key("dropper_x2"), new ItemStack(Material.DROPPER, 2))
                .shape("CCC", "C C", "CRC")
                .setIngredient('C', Material.COBBLESTONE)
                .setIngredient('R', Material.REDSTONE));
        // ディスペンサー 2個
        add(new ShapedRecipe(key("dispenser_x2"), new ItemStack(Material.DISPENSER, 2))
                .shape("CCC", "CBC", "CRC")
                .setIngredient('C', Material.COBBLESTONE)
                .setIngredient('B', Material.BOW)
                .setIngredient('R', Material.REDSTONE));
        // ホッパー 2個
        add(new ShapedRecipe(key("hopper_x2"), new ItemStack(Material.HOPPER, 2))
                .shape("I I", "ICI", "III")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('C', Material.CHEST));
        // ピストン 2個
        add(new ShapedRecipe(key("piston_x2"), new ItemStack(Material.PISTON, 2))
                .shape("WWW", "ISI", "IRI")
                .setIngredient('W', Material.OAK_PLANKS)
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('S', Material.COBBLESTONE)
                .setIngredient('R', Material.REDSTONE));
        // ターゲットブロック 2個
        add(new ShapedRecipe(key("target_x2"), new ItemStack(Material.TARGET, 2))
                .shape("HRH", "RHR", "HRH")
                .setIngredient('H', Material.HAY_BLOCK)
                .setIngredient('R', Material.REDSTONE));
        // 時計 2個
        add(new ShapedRecipe(key("clock_x2"), new ItemStack(Material.CLOCK, 2))
                .shape("GGG", "GRG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('R', Material.REDSTONE));
        // コンパス 2個
        add(new ShapedRecipe(key("compass_x2"), new ItemStack(Material.COMPASS, 2))
                .shape("III", "IRI")
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('R', Material.REDSTONE));
        // 生鉄ブロック → 鉄インゴット 9個
        add(shapeless("raw_iron_block_decompose", new ItemStack(Material.IRON_INGOT, 9), Material.RAW_IRON_BLOCK));
    }

    // =====================================================================
    // ユーティリティ
    // =====================================================================
    private NamespacedKey key(String id) { return new NamespacedKey(plugin, id); }

    private ShapelessRecipe shapeless(String id, ItemStack result, Material... mats) {
        ShapelessRecipe r = new ShapelessRecipe(key(id), result);
        for (Material m : mats) r.addIngredient(m);
        return r;
    }

    private void add(org.bukkit.inventory.Recipe recipe) {
        plugin.getServer().addRecipe(recipe);
    }
}
