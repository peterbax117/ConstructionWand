package thetadev.constructionwand.basics.option;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.api.IWandUpgrade;
import thetadev.constructionwand.basics.ReplacementRegistry;
import thetadev.constructionwand.items.core.CoreDefault;

import javax.annotation.Nullable;

public class WandOptions
{
    public final CompoundTag tag;
    private final ItemStack stack;

    private static final String TAG_ROOT = "wand_options";

    public enum LOCK
    {
        HORIZONTAL,
        VERTICAL,
        NORTHSOUTH,
        EASTWEST,
        NOLOCK
    }

    public enum DIRECTION
    {
        TARGET,
        PLAYER
    }

    public enum MATCH
    {
        EXACT,
        SIMILAR,
        ANY
    }

    public final WandUpgradesSelectable<IWandCore> cores;

    public final OptionEnum<LOCK> lock;
    public final OptionEnum<DIRECTION> direction;
    public final OptionBoolean replace;
    public final OptionEnum<MATCH> match;
    public final OptionBoolean random;

    public final IOption<?>[] allOptions;

    public WandOptions(ItemStack wandStack) {
        this.stack = wandStack;
        // 1.21 replaced item NBT with data components. Options live in minecraft:custom_data under the
        // same "wand_options" key, so wands from 1.20 worlds keep their settings after the vanilla upgrade.
        tag = wandStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG_ROOT);

        cores = new WandUpgradesSelectable<>(tag, this::save, "cores", new CoreDefault());

        lock = new OptionEnum<>(tag, this::save, "lock", LOCK.class, LOCK.NOLOCK);
        direction = new OptionEnum<>(tag, this::save, "direction", DIRECTION.class, DIRECTION.TARGET);
        replace = new OptionBoolean(tag, this::save, "replace", true);
        match = new OptionEnum<>(tag, this::save, "match", MATCH.class, MATCH.SIMILAR);
        random = new OptionBoolean(tag, this::save, "random", false);

        allOptions = new IOption[]{cores, lock, direction, replace, match, random};
    }

    // Components are immutable snapshots, so every change has to be written back to the stack.
    private void save() {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG_ROOT, tag.copy()));
    }

    @Nullable
    public IOption<?> get(String key) {
        for(IOption<?> option : allOptions) {
            if(option.getKey().equals(key)) return option;
        }
        return null;
    }

    public boolean testLock(LOCK l) {
        if(lock.get() == LOCK.NOLOCK) return true;
        return lock.get() == l;
    }

    public boolean matchBlocks(Block b1, Block b2) {
        switch(match.get()) {
            case EXACT:
                return b1 == b2;
            case SIMILAR:
                return ReplacementRegistry.matchBlocks(b1, b2);
            case ANY:
                return b1 != Blocks.AIR && b2 != Blocks.AIR;
        }
        return false;
    }

    public boolean hasUpgrade(IWandUpgrade upgrade) {
        if(upgrade instanceof IWandCore) return cores.hasUpgrade((IWandCore) upgrade);
        return false;
    }

    public boolean addUpgrade(IWandUpgrade upgrade) {
        if(upgrade instanceof IWandCore) return cores.addUpgrade((IWandCore) upgrade);
        return false;
    }
}
