package net.circuitsurvival.listeners;

import net.circuitsurvival.items.CustomItems;
import net.circuitsurvival.items.IntermediateMaterials;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 料理 / PixelGun 元ネタカスタムアイテムの挙動
 *
 * ■ 料理系
 *   爆発コロッケ  : 右クリックで投射 → 着弾時爆発(Power 2)
 *   シェフナイフ  : 命中時 Poison I (40t) + Slowness II (60t)
 *   特製スパイスカレー : 食べると Speed III + Strength II + Fire Resistance (400t)
 *
 * ■ PixelGun系
 *   ピクセルガン      : 弓を射ると弾を追跡タグ付け → 命中エンティティに Glowing 200t
 *   ダークセイバー    : 命中時 Wither II (100t) + Blindness (80t)
 *   グレネードランチャー : 矢を追跡タグ付け → 着弾時爆発(Power 3)
 *   スナイパーライフル  : 射出時 Night Vision 60t → 命中エンティティに +20 ダメージ
 */
public class CustomItemListener implements Listener {

    // ---- 中間素材・カスタムアイテムの誤設置防止 ---------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreventPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = switch (event.getHand()) {
            case HAND -> event.getPlayer().getInventory().getItemInMainHand();
            case OFF_HAND -> event.getPlayer().getInventory().getItemInOffHand();
            default -> null;
        };
        if (item == null) return;
        if (!item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();

        // 中間素材は絶対に設置させない
        if (pdc.has(IntermediateMaterials.KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // ---- 中間素材はバニラクラフトに使わせない (増殖防止) ------------------------
    // 材料にPDCがある場合、結果もPDCなら許可、結果がバニラならブロック

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        boolean hasCustomIngredient = false;
        for (ItemStack ing : event.getInventory().getMatrix()) {
            if (ing == null || !ing.hasItemMeta()) continue;
            var pdc = ing.getItemMeta().getPersistentDataContainer();
            if (pdc.has(IntermediateMaterials.KEY, org.bukkit.persistence.PersistentDataType.STRING)
                    || pdc.has(CustomItems.KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                hasCustomIngredient = true;
                break;
            }
        }
        if (!hasCustomIngredient) return;

        // 結果もカスタムPDCを持つなら許可、バニラ結果のみブロック
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.hasItemMeta()) {
            var rpdc = result.getItemMeta().getPersistentDataContainer();
            if (!rpdc.has(IntermediateMaterials.KEY, org.bukkit.persistence.PersistentDataType.STRING)
                    && !rpdc.has(CustomItems.KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                event.getInventory().setResult(null);
            }
        } else if (result != null) {
            // result has no ItemMeta → vanilla item → block
            event.getInventory().setResult(null);
        }
    }

    // ---- 爆発コロッケ: 右クリックで投射 ----------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CustomItems.is(item, CustomItems.EXPLOSIVE_CROQUETTE)) return;

        event.setCancelled(true);

        // スタックを1個消費
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Snowball を射出して爆発追跡タグを付与
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(
                CustomItems.PROJECTILE_KEY, PersistentDataType.STRING,
                CustomItems.EXPLOSIVE_CROQUETTE);
    }

    // ---- 弓/クロスボウ発射: PixelGun / グレネードランチャー / スナイパー ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity projectile = event.getProjectile();

        ItemStack bow = event.getBow();
        String id = CustomItems.getId(bow);
        if (id == null) return;

        switch (id) {
            case CustomItems.PIXEL_GUN -> projectile.getPersistentDataContainer()
                    .set(CustomItems.PROJECTILE_KEY, PersistentDataType.STRING, CustomItems.PIXEL_GUN);

            case CustomItems.GRENADE_LAUNCHER -> projectile.getPersistentDataContainer()
                    .set(CustomItems.PROJECTILE_KEY, PersistentDataType.STRING, CustomItems.GRENADE_LAUNCHER);

            case CustomItems.SNIPER_RIFLE -> {
                projectile.getPersistentDataContainer()
                        .set(CustomItems.PROJECTILE_KEY, PersistentDataType.STRING, CustomItems.SNIPER_RIFLE);
                // 発射時 Night Vision 3秒付与
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60, 0, false, false));
            }
        }
    }

    // ---- 発射物着弾: 爆発コロッケ / グレネードランチャー / PixelGun / スナイパー ------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity proj = event.getEntity();
        String tag = proj.getPersistentDataContainer()
                .get(CustomItems.PROJECTILE_KEY, PersistentDataType.STRING);
        if (tag == null) return;

        ProjectileSource shooter = (proj instanceof Projectile p) ? p.getShooter() : null;
        Entity shooterEntity = (shooter instanceof Entity e) ? e : null;

        switch (tag) {
            case CustomItems.EXPLOSIVE_CROQUETTE ->
                    proj.getWorld().createExplosion(proj.getLocation(), 2.0f, false, false, shooterEntity);

            case CustomItems.GRENADE_LAUNCHER ->
                    proj.getWorld().createExplosion(proj.getLocation(), 3.0f, false, false, shooterEntity);

            case CustomItems.PIXEL_GUN -> {
                Entity hitEntity = event.getHitEntity();
                if (hitEntity instanceof LivingEntity le) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, true));
                }
            }

            case CustomItems.SNIPER_RIFLE -> {
                Entity hitEntity = event.getHitEntity();
                if (hitEntity instanceof LivingEntity le && shooterEntity != null) {
                    le.damage(20.0, shooterEntity);
                }
            }
        }
    }

    // ---- 近接攻撃: シェフナイフ / ダークセイバー --------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        String id = CustomItems.getId(held);
        if (id == null) return;

        switch (id) {
            case CustomItems.CHEF_KNIFE -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,   60, 1, false, true));
            }
            case CustomItems.DARK_SABER -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,    100, 1, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,  80, 0, false, true));
            }
            case CustomItems.NANO_SWORD -> {
                // 追加ダメージ +4
                event.setDamage(event.getDamage() + 4.0);
            }
        }
    }

    // ---- フォースアーマーセットボーナス --------------------------------------------

    private static final String[] FORCE_ARMOR_IDS = {
            CustomItems.FORCE_HELMET, CustomItems.FORCE_CHESTPLATE,
            CustomItems.FORCE_LEGGINGS, CustomItems.FORCE_BOOTS
    };

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        EntityEquipment eq = player.getEquipment();
        if (eq == null) return;

        // フルセット装備確認
        boolean fullSet = true;
        ItemStack[] armor = eq.getArmorContents();
        for (int i = 0; i < 4; i++) {
            ItemStack piece = armor[i];
            String id = CustomItems.getId(piece);
            if (!FORCE_ARMOR_IDS[i].equals(id)) {
                fullSet = false;
                break;
            }
        }

        if (fullSet) {
            // セットボーナス: 耐性 + 再生
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false));
        }
    }

    // ---- 食事: 特製スパイスカレー -----------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!CustomItems.is(event.getItem(), CustomItems.SPICE_CURRY)) return;
        Player player = event.getPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           400, 2, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 400, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0, false, true));
    }
}
