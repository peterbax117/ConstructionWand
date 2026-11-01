package thetadev.constructionwand.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.crafting.RecipeWandUpgrade;
import thetadev.constructionwand.items.core.ItemCoreAngel;
import thetadev.constructionwand.items.core.ItemCoreDestruction;
import thetadev.constructionwand.items.wand.ItemWandBasic;
import thetadev.constructionwand.items.wand.ItemWandInfinity;

@EventBusSubscriber(modid = ConstructionWand.MODID)
public class ModItems
{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ConstructionWand.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ConstructionWand.MODID);

    // Wands
    public static final DeferredItem<Item> WAND_STONE = ITEMS.register("stone_wand", () -> new ItemWandBasic(propWand(), Tiers.STONE));
    public static final DeferredItem<Item> WAND_IRON = ITEMS.register("iron_wand", () -> new ItemWandBasic(propWand(), Tiers.IRON));
    public static final DeferredItem<Item> WAND_DIAMOND = ITEMS.register("diamond_wand", () -> new ItemWandBasic(propWand(), Tiers.DIAMOND));
    public static final DeferredItem<Item> WAND_INFINITY = ITEMS.register("infinity_wand", () -> new ItemWandInfinity(propWand()));

    // Cores
    public static final DeferredItem<Item> CORE_ANGEL = ITEMS.register("core_angel", () -> new ItemCoreAngel(propUpgrade()));
    public static final DeferredItem<Item> CORE_DESTRUCTION = ITEMS.register("core_destruction", () -> new ItemCoreDestruction(propUpgrade()));

    // Collections
    @SuppressWarnings("unchecked")
    public static final DeferredItem<Item>[] WANDS = new DeferredItem[] {WAND_STONE, WAND_IRON, WAND_DIAMOND, WAND_INFINITY};
    @SuppressWarnings("unchecked")
    public static final DeferredItem<Item>[] CORES = new DeferredItem[] {CORE_ANGEL, CORE_DESTRUCTION};

    // Recipe serializers
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RecipeWandUpgrade>> WAND_UPGRADE =
            RECIPE_SERIALIZERS.register("wand_upgrade", () -> RecipeWandUpgrade.SERIALIZER);

    public static Item.Properties propWand() {
        return new Item.Properties();
    }

    private static Item.Properties propUpgrade() {
        return new Item.Properties().stacksTo(1);
    }

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for(DeferredItem<Item> item : WANDS) event.accept(item);
        }
        else if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for(DeferredItem<Item> item : CORES) event.accept(item);
        }
    }
}
