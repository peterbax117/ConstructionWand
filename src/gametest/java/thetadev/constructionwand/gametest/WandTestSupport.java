package thetadev.constructionwand.gametest;

import io.github.peterbax117.modtestharness.gametest.GameTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.basics.option.WandOptions;

import java.util.function.Consumer;

/** Construction Wand specific fixtures on top of the harness's GameTestSupport. */
final class WandTestSupport
{
    private WandTestSupport() {
    }

    /** Survival player south of the small test area, holding the wand. */
    static FakePlayer survivalPlayer(GameTestHelper helper, ItemStack wand) {
        return GameTestSupport.survivalPlayer(helper, wand, new BlockPos(4, 2, 0), 0, 0);
    }

    static ItemStack wand(Item wandItem, Consumer<WandOptions> configure) {
        ItemStack wand = new ItemStack(wandItem);
        configure.accept(new WandOptions(wand));
        return wand;
    }

    /** A wand with the core installed and selected. */
    static ItemStack wandWithCore(Item wandItem, Item core) {
        return wand(wandItem, options -> {
            options.addUpgrade((IWandCore) core);
            options.cores.set((IWandCore) core);
        });
    }

    /** 3x3 stone floor at y=1 in the middle of the small test area. */
    static void buildFloor(GameTestHelper helper) {
        GameTestSupport.fill(helper, Blocks.STONE, 3, 5, 1, 3, 5);
    }

    /** Asserts the 3x3 layer above/at the small floor is the given block. */
    static void assertLayer(GameTestHelper helper, Block block, int y) {
        GameTestSupport.assertBox(helper, block, new BlockPos(3, y, 3), new BlockPos(5, y, 5));
    }

    static InteractionResult useWandOn(GameTestHelper helper, Player player, BlockPos relative, Direction face) {
        return GameTestSupport.useItemOn(helper, player, relative, face);
    }

    static void useWandInAir(GameTestHelper helper, Player player) {
        GameTestSupport.useItemInAir(helper, player);
    }
}
