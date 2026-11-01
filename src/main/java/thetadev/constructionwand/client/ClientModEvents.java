package thetadev.constructionwand.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.items.wand.ItemWand;

// Client-only mod bus registrations, kept out of common classes so dedicated servers never load them.
@EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT)
public class ClientModEvents
{
    public static void registerModelProperties() {
        for(DeferredItem<Item> wand : ModItems.WANDS) {
            ItemProperties.register(
                    wand.get(), ConstructionWand.loc("using_core"),
                    (stack, world, entity, n) -> entity == null || !(stack.getItem() instanceof ItemWand) ? 0 :
                            new WandOptions(stack).cores.get().getColor() > -1 ? 1 : 0
            );
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for(DeferredItem<Item> wand : ModItems.WANDS) {
            // Since 1.21 item tints are ARGB, so an RGB core color needs a fully opaque alpha channel.
            event.register((stack, layer) -> {
                if(layer != 1 || !(stack.getItem() instanceof ItemWand)) return -1;
                int color = new WandOptions(stack).cores.get().getColor();
                return color == -1 ? -1 : FastColor.ARGB32.opaque(color);
            }, wand.get());
        }
    }
}
