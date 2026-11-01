package thetadev.constructionwand.containers.handlers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import thetadev.constructionwand.api.IContainerHandler;
import thetadev.constructionwand.basics.WandUtil;

import java.util.ArrayList;
import java.util.List;

public class HandlerBundle implements IContainerHandler
{
    @Override
    public boolean matches(Player player, ItemStack itemStack, ItemStack inventoryStack) {
        return inventoryStack != null && inventoryStack.getCount() == 1 && inventoryStack.getItem() == Items.BUNDLE;
    }

    @Override
    public int countItems(Player player, ItemStack itemStack, ItemStack inventoryStack) {
        int total = 0;
        for(ItemStack stack : getContents(inventoryStack)) {
            if(WandUtil.stackEquals(stack, itemStack)) total += stack.getCount();
        }
        return total;
    }

    @Override
    public int useItems(Player player, ItemStack itemStack, ItemStack inventoryStack, int count) {
        List<ItemStack> remaining = new ArrayList<>();

        for(ItemStack stack : getContents(inventoryStack)) {
            if(count > 0 && WandUtil.stackEquals(stack, itemStack)) {
                int toTake = Math.min(count, stack.getCount());
                stack.shrink(toTake);
                count -= toTake;
            }
            if(!stack.isEmpty()) remaining.add(stack);
        }

        inventoryStack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(remaining));
        return count;
    }

    // Returns copies, because component contents must not be mutated in place.
    private List<ItemStack> getContents(ItemStack bundleStack) {
        BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
        if(contents == null) return new ArrayList<>();

        List<ItemStack> stacks = new ArrayList<>();
        contents.itemsCopy().forEach(stacks::add);
        return stacks;
    }
}
