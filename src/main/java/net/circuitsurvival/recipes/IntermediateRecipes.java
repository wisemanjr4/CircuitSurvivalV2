package net.circuitsurvival.recipes;

import net.circuitsurvival.items.IntermediateMaterials;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/**
 * 中間素材のバニラクラフトレシピ。
 * これらのレシピで生成されるアイテムはPDCタグを持ち、
 * カスタムクラフターでの自動生産でも使用される。
 */
public class IntermediateRecipes {

    private final Plugin plugin;

    public IntermediateRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        // Tier 1
        add(new ShapedRecipe(key("circuit_board"), IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD))
                .shape("RCR", "C C", "RCR")
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('C', Material.COPPER_INGOT));
        add(new ShapedRecipe(key("machine_gear"), IntermediateMaterials.build(IntermediateMaterials.MACHINE_GEAR, 4))
                .shape(" I ", "I I", " I ")
                .setIngredient('I', Material.IRON_NUGGET));
        add(new ShapedRecipe(key("machine_casing"), IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING))
                .shape("III", "I I", "III")
                .setIngredient('I', Material.IRON_INGOT));

        // Tier 2
        add(new ShapedRecipe(key("processor_unit"), IntermediateMaterials.build(IntermediateMaterials.PROCESSOR_UNIT))
                .shape("CQC", "QCQ", "CQC")
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.CIRCUIT_BOARD)))
                .setIngredient('Q', Material.QUARTZ));
        add(new ShapedRecipe(key("motor"), IntermediateMaterials.build(IntermediateMaterials.MOTOR))
                .shape(" G ", "GPG", " G ")
                .setIngredient('G', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MACHINE_GEAR)))
                .setIngredient('P', Material.PISTON));
        add(new ShapedRecipe(key("heating_coil"), IntermediateMaterials.build(IntermediateMaterials.HEATING_COIL, 2))
                .shape(" B ", "BCB", " B ")
                .setIngredient('B', Material.BLAZE_POWDER)
                .setIngredient('C', Material.COPPER_INGOT));
        add(new ShapedRecipe(key("cooling_cell"), IntermediateMaterials.build(IntermediateMaterials.COOLING_CELL))
                .shape("C C", "CIC", "C C")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('I', Material.PACKED_ICE));
        add(new ShapedRecipe(key("reinforced_casing"), IntermediateMaterials.build(IntermediateMaterials.REINFORCED_CASING))
                .shape("OCO", "C C", "OCO")
                .setIngredient('O', Material.OBSIDIAN)
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.MACHINE_CASING))));

        // Tier 3
        add(new ShapedRecipe(key("energy_core"), IntermediateMaterials.build(IntermediateMaterials.ENERGY_CORE))
                .shape("DGD", "EPE", "DGD")
                .setIngredient('D', Material.DIAMOND)
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('E', Material.REDSTONE_BLOCK)
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PROCESSOR_UNIT))));
        add(new ShapedRecipe(key("stabilizer"), IntermediateMaterials.build(IntermediateMaterials.STABILIZER))
                .shape("SAS", "ACA", "SAS")
                .setIngredient('S', Material.NETHERITE_SCRAP)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('C', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.ENERGY_CORE))));

        // Chain 5: 高度冶金 (Phase 6)
        add(new ShapedRecipe(key("reinforced_plate"),
                IntermediateMaterials.build(IntermediateMaterials.REINFORCED_PLATE, 2))
                .shape("APA", "P P", "APA")
                .setIngredient('A', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.HARDENED_ALLOY)))
                .setIngredient('P', new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.PLASTIC))));

        // Chain 1: 粉砕物→精錬 (かまど代替、機械より効率は劣る)
        add(new FurnaceRecipe(key("refined_iron_smelt"),
                IntermediateMaterials.build(IntermediateMaterials.REFINED_IRON_INGOT),
                new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.CRUSHED_IRON)),
                0.7f, 200));
        add(new FurnaceRecipe(key("refined_gold_smelt"),
                IntermediateMaterials.build(IntermediateMaterials.REFINED_GOLD_INGOT),
                new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.CRUSHED_GOLD)),
                1.0f, 200));
        // Chain 1: 圧縮・解凍 (クラフト台代替)
        add(new ShapelessRecipe(key("compress_iron"),
                IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON))
                .addIngredient(Material.IRON_BLOCK));
        add(new ShapelessRecipe(key("compress_gold"),
                IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD))
                .addIngredient(Material.GOLD_BLOCK));
        add(new ShapelessRecipe(key("decompress_iron"),
                new ItemStack(Material.IRON_BLOCK))
                .addIngredient(new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_IRON))));
        add(new ShapelessRecipe(key("decompress_gold"),
                new ItemStack(Material.GOLD_BLOCK))
                .addIngredient(new RecipeChoice.ExactChoice(
                        IntermediateMaterials.build(IntermediateMaterials.COMPRESSED_GOLD))));

        // Tier 2.5: 高度部品
        add(new ShapedRecipe(key("advanced_circuit"), IntermediateMaterials.build(IntermediateMaterials.ADVANCED_CIRCUIT))
                .shape("GRG", "RPR", "GRG")
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('R', Material.REDSTONE_BLOCK)
                .setIngredient('P', Material.REPEATER));
        add(new ShapedRecipe(key("high_gear"), IntermediateMaterials.build(IntermediateMaterials.HIGH_GEAR))
                .shape(" I ", "IGI", " I ")
                .setIngredient('I', Material.IRON_BLOCK)
                .setIngredient('G', Material.IRON_NUGGET));

        // Chain 3: 機械部品
        add(new ShapedRecipe(key("en_cell_component"),
                IntermediateMaterials.build(IntermediateMaterials.EN_CELL_COMPONENT, 2))
                .shape("LGL", "GRG", "LGL")
                .setIngredient('L', Material.LAPIS_LAZULI)
                .setIngredient('G', Material.GOLD_INGOT)
                .setIngredient('R', Material.REDSTONE_BLOCK));
        add(new ShapedRecipe(key("pipe_connector"),
                IntermediateMaterials.build(IntermediateMaterials.PIPE_CONNECTOR, 16))
                .shape(" S ", "SRS", " S ")
                .setIngredient('S', Material.IRON_INGOT)
                .setIngredient('R', Material.REDSTONE));
    }

    private NamespacedKey key(String id) { return new NamespacedKey(plugin, id); }

    private void add(org.bukkit.inventory.Recipe recipe) {
        plugin.getServer().addRecipe(recipe);
        if (recipe instanceof org.bukkit.Keyed k) RecipeKeyRegistry.register(k.getKey());
    }
}
