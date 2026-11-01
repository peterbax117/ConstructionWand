package thetadev.constructionwand.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thetadev.constructionwand.api.IWandCore;
import thetadev.constructionwand.basics.option.WandOptions;

import java.util.UUID;
import java.util.function.Consumer;

/** Shared fixtures for the GameTests. Positions are relative to the test structure unless noted. */
final class GameTestSupport
{
    private GameTestSupport() {
    }

    /** Survival fake player with a unique profile, standing at the given relative position, holding the wand. */
    static FakePlayer survivalPlayer(GameTestHelper helper, ItemStack wand, BlockPos standing, float yRot, float xRot) {
        // A unique profile per test keeps undo history and inventories from leaking between tests.
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wand-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.fallDistance = 0;
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(standing));
        player.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    static FakePlayer survivalPlayer(GameTestHelper helper, ItemStack wand) {
        return survivalPlayer(helper, wand, new BlockPos(4, 2, 0), 0, 0);
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

    static void fill(GameTestHelper helper, BlockState state, int x1, int x2, int y, int z1, int z2) {
        for(int x = x1; x <= x2; x++) for(int z = z1; z <= z2; z++) helper.setBlock(new BlockPos(x, y, z), state);
    }

    static void fill(GameTestHelper helper, Block block, int x1, int x2, int y, int z1, int z2) {
        fill(helper, block.defaultBlockState(), x1, x2, y, z1, z2);
    }

    /** Counts blocks of the given type anywhere in the test structure. forEveryBlockInStructure yields relative positions. */
    static int countInStructure(GameTestHelper helper, Block block) {
        int[] count = {0};
        helper.forEveryBlockInStructure(pos -> {
            if(helper.getBlockState(pos).is(block)) count[0]++;
        });
        return count[0];
    }

    static int countInLayer(GameTestHelper helper, Block block, int y) {
        int[] count = {0};
        helper.forEveryBlockInStructure(pos -> {
            if(pos.getY() == y && helper.getBlockState(pos).is(block)) count[0]++;
        });
        return count[0];
    }

    static InteractionResult useWandOn(GameTestHelper helper, Player player, BlockPos relative, Direction face) {
        BlockPos pos = helper.absolutePos(relative);
        Vec3 hitLocation = Vec3.atCenterOf(pos).relative(face, 0.5);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitLocation, face, pos, false));
        return player.getMainHandItem().useOn(context);
    }

    /** Right click into the air, as used by the angel core. */
    static void useWandInAir(GameTestHelper helper, Player player) {
        player.getMainHandItem().getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    static void assertLayer(GameTestHelper helper, Block block, int y) {
        for(int x = 3; x <= 5; x++) for(int z = 3; z <= 5; z++) helper.assertBlockPresent(block, new BlockPos(x, y, z));
    }

    static void buildFloor(GameTestHelper helper) {
        fill(helper, net.minecraft.world.level.block.Blocks.STONE, 3, 5, 1, 3, 5);
    }

    static int countItem(Player player, Item item) {
        int total = 0;
        for(ItemStack stack : player.getInventory().items) if(stack.is(item)) total += stack.getCount();
        for(ItemStack stack : player.getInventory().offhand) if(stack.is(item)) total += stack.getCount();
        return total;
    }

    static void giveStacks(Player player, Item item, int count) {
        int slot = 1;
        while(count > 0) {
            int size = Math.min(64, count);
            player.getInventory().setItem(slot++, new ItemStack(item, size));
            count -= size;
        }
    }
}
