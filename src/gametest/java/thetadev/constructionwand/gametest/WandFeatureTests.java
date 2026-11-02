package thetadev.constructionwand.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import io.github.peterbax117.modtestharness.gametest.HarnessTemplates;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.ModItems;

import java.util.List;
import java.util.Optional;

import static io.github.peterbax117.modtestharness.gametest.GameTestSupport.*;
import static thetadev.constructionwand.gametest.WandTestSupport.*;

/**
 * Feature coverage: every wand tier, both cores, every wand option, supply rules and server config limits.
 * Expected numbers come from the default server config (ConfigServer).
 */
// Uses the harness's shared empty templates, so the holder namespace is the harness namespace.
@GameTestHolder(HarnessTemplates.NAMESPACE)
@PrefixGameTestTemplate(false)
public class WandFeatureTests
{
    private static final String SMALL = "empty_9x5x9";
    private static final String LARGE = "empty_15x8x15";
    private static final BlockPos LARGE_CENTER = new BlockPos(7, 1, 7);
    private static final BlockPos SMALL_CENTER = new BlockPos(4, 1, 4);

    // Wand tiers: block limit and durability. Large floor = 13x13 = 169 positions.

    @GameTest(template = LARGE)
    public static void stoneWandLimitIs9(GameTestHelper helper) {
        assertPlacementLimit(helper, ModItems.WAND_STONE.get(), 9);
    }

    @GameTest(template = LARGE)
    public static void ironWandLimitIs27(GameTestHelper helper) {
        assertPlacementLimit(helper, ModItems.WAND_IRON.get(), 27);
    }

    @GameTest(template = LARGE)
    public static void diamondWandLimitIs128(GameTestHelper helper) {
        assertPlacementLimit(helper, ModItems.WAND_DIAMOND.get(), 128);
    }

    @GameTest(template = LARGE)
    public static void infinityWandFillsFloorWithoutDamage(GameTestHelper helper) {
        fill(helper, Blocks.STONE, 1, 13, 1, 1, 13);
        ItemStack wand = new ItemStack(ModItems.WAND_INFINITY.get());
        FakePlayer player = survivalPlayer(helper, wand, new BlockPos(7, 2, 0), 0, 0);
        giveStacks(player, Items.STONE, 192);

        useWandOn(helper, player, LARGE_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), 169, "blocks placed by infinity wand (limit 1024)");
        helper.assertFalse(wand.isDamageableItem(), "Infinity wand must not take damage");
        helper.assertTrue(player.getMainHandItem() == wand, "Infinity wand should still be held");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void wandBreaksWhenDurabilityRunsOut(GameTestHelper helper) {
        buildFloor(helper);
        ItemStack wand = new ItemStack(ModItems.WAND_STONE.get());
        wand.setDamageValue(wand.getMaxDamage() - 3);
        FakePlayer player = survivalPlayer(helper, wand);
        giveStacks(player, Items.STONE, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), 3, "blocks placed with 3 durability left");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Wand should break when its durability runs out");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void wandsRepairWithTheirTierMaterial(GameTestHelper helper) {
        ItemStack stone = new ItemStack(ModItems.WAND_STONE.get());
        ItemStack iron = new ItemStack(ModItems.WAND_IRON.get());
        ItemStack diamond = new ItemStack(ModItems.WAND_DIAMOND.get());
        ItemStack infinity = new ItemStack(ModItems.WAND_INFINITY.get());

        helper.assertTrue(stone.getItem().isValidRepairItem(stone, new ItemStack(Items.COBBLESTONE)), "Stone wand repairs with cobblestone");
        helper.assertTrue(iron.getItem().isValidRepairItem(iron, new ItemStack(Items.IRON_INGOT)), "Iron wand repairs with iron ingots");
        helper.assertTrue(diamond.getItem().isValidRepairItem(diamond, new ItemStack(Items.DIAMOND)), "Diamond wand repairs with diamonds");
        helper.assertFalse(iron.getItem().isValidRepairItem(iron, new ItemStack(Items.DIAMOND)), "Iron wand must not repair with diamonds");
        helper.assertFalse(infinity.getItem().isValidRepairItem(infinity, new ItemStack(Items.NETHER_STAR)), "Infinity wand is not repairable");
        helper.succeed();
    }

    // Destruction core: removes the connected layer up to the tier's destruction limit.

    @GameTest(template = LARGE)
    public static void ironDestructionLimitIs9(GameTestHelper helper) {
        assertDestructionLimit(helper, ModItems.WAND_IRON.get(), 9);
    }

    @GameTest(template = LARGE)
    public static void diamondDestructionLimitIs25(GameTestHelper helper) {
        assertDestructionLimit(helper, ModItems.WAND_DIAMOND.get(), 25);
    }

    @GameTest(template = LARGE)
    public static void infinityDestructionLimitIs81(GameTestHelper helper) {
        assertDestructionLimit(helper, ModItems.WAND_INFINITY.get(), 81);
    }

    // Angel core

    @GameTest(template = SMALL)
    public static void angelCorePlacesBehindClickedBlock(GameTestHelper helper) {
        BlockPos floating = new BlockPos(4, 3, 4);
        helper.setBlock(floating, Blocks.STONE);
        FakePlayer player = survivalPlayer(helper, wandWithCore(ModItems.WAND_IRON.get(), ModItems.CORE_ANGEL.get()));
        giveStacks(player, Items.STONE, 64);

        // Clicking the top face places a block on the opposite side, below the clicked block.
        useWandOn(helper, player, floating, Direction.UP);

        helper.assertBlockPresent(Blocks.STONE, floating.below());
        helper.assertBlockPresent(Blocks.AIR, floating.above());
        helper.assertValueEqual(countItem(player, Items.STONE), 63, "stone left after one angel placement");
        helper.succeed();
    }

    @GameTest(template = LARGE)
    public static void angelCorePlacesInMidAir(GameTestHelper helper) {
        FakePlayer player = survivalPlayer(helper, wandWithCore(ModItems.WAND_IRON.get(), ModItems.CORE_ANGEL.get()),
                new BlockPos(7, 1, 7), 0, -90);

        // Per the README, mid-air placement only uses the block in the off hand.
        giveStacks(player, Items.STONE, 64);
        useWandInAir(helper, player);
        helper.assertValueEqual(countInStructure(helper, Blocks.STONE), 0, "blocks conjured with an empty off hand");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
        useWandInAir(helper, player);

        helper.assertValueEqual(countInStructure(helper, Blocks.STONE), 1, "blocks conjured in mid air");
        // The block type comes from the off hand, but items are taken from the inventory first (original behavior).
        helper.assertValueEqual(countItem(player, Items.STONE), 64 + 16 - 1, "stone used in total");
        helper.forEveryBlockInStructure(pos -> {
            if(helper.getBlockState(pos).is(Blocks.STONE)) {
                for(Direction side : Direction.values()) {
                    helper.assertTrue(helper.getBlockState(pos.relative(side)).isAir(), "Angel block should be floating");
                }
            }
        });
        helper.succeed();
    }

    @GameTest(template = LARGE)
    public static void angelCoreRefusesAfterLongFall(GameTestHelper helper) {
        FakePlayer player = survivalPlayer(helper, wandWithCore(ModItems.WAND_IRON.get(), ModItems.CORE_ANGEL.get()),
                new BlockPos(7, 1, 7), 0, -90);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
        player.fallDistance = 11;

        useWandInAir(helper, player);

        helper.assertValueEqual(countInStructure(helper, Blocks.STONE), 0, "blocks conjured after falling 11 blocks (AngelFalling off)");
        helper.succeed();
    }

    // Lock option: which directions the placement may spread in.

    @GameTest(template = SMALL)
    public static void northSouthLockOnFloorPlacesRow(GameTestHelper helper) {
        assertFloorLock(helper, WandOptions.LOCK.NORTHSOUTH, 3, Axis.Z);
    }

    @GameTest(template = SMALL)
    public static void eastWestLockOnFloorPlacesRow(GameTestHelper helper) {
        assertFloorLock(helper, WandOptions.LOCK.EASTWEST, 3, Axis.X);
    }

    @GameTest(template = SMALL)
    public static void horizontalLockDoesNotApplyToFloors(GameTestHelper helper) {
        assertFloorLock(helper, WandOptions.LOCK.HORIZONTAL, 0, null);
    }

    @GameTest(template = SMALL)
    public static void horizontalLockOnWallPlacesRow(GameTestHelper helper) {
        assertWallLock(helper, WandOptions.LOCK.HORIZONTAL, Axis.X);
    }

    @GameTest(template = SMALL)
    public static void verticalLockOnWallPlacesColumn(GameTestHelper helper) {
        assertWallLock(helper, WandOptions.LOCK.VERTICAL, Axis.Y);
    }

    // Direction option: copy rotation from the supporting block, or place like the player would.

    @GameTest(template = SMALL)
    public static void targetDirectionCopiesLogAxis(GameTestHelper helper) {
        assertLogAxis(helper, WandOptions.DIRECTION.TARGET, Axis.X);
    }

    @GameTest(template = SMALL)
    public static void playerDirectionUsesClickedFace(GameTestHelper helper) {
        assertLogAxis(helper, WandOptions.DIRECTION.PLAYER, Axis.Y);
    }

    // Match option: which supporting blocks count as part of the surface.

    @GameTest(template = SMALL)
    public static void exactMatchOnlyExtendsIdenticalBlocks(GameTestHelper helper) {
        assertMatch(helper, WandOptions.MATCH.EXACT, Blocks.STONE, Blocks.ANDESITE, Items.STONE, 1);
    }

    @GameTest(template = SMALL)
    public static void anyMatchExtendsAllBlocks(GameTestHelper helper) {
        assertMatch(helper, WandOptions.MATCH.ANY, Blocks.STONE, Blocks.ANDESITE, Items.STONE, 9);
    }

    @GameTest(template = SMALL)
    public static void similarMatchTreatsDirtAndGrassAlike(GameTestHelper helper) {
        // Dirt and grass are in the default SimilarBlocks config list.
        assertMatch(helper, WandOptions.MATCH.SIMILAR, Blocks.DIRT, Blocks.GRASS_BLOCK, Items.DIRT, 9);
    }

    @GameTest(template = SMALL)
    public static void similarMatchKeepsUnrelatedBlocksApart(GameTestHelper helper) {
        assertMatch(helper, WandOptions.MATCH.SIMILAR, Blocks.STONE, Blocks.ANDESITE, Items.STONE, 1);
    }

    // Replace option: whether replaceable blocks such as a snow layer may be overwritten.

    @GameTest(template = SMALL)
    public static void replaceOnOverwritesSnowLayer(GameTestHelper helper) {
        assertReplace(helper, true, 9);
    }

    @GameTest(template = SMALL)
    public static void replaceOffSkipsSnowLayer(GameTestHelper helper) {
        assertReplace(helper, false, 8);
    }

    // Supply rules

    @GameTest(template = LARGE)
    public static void randomModeMixesHotbarBlocks(GameTestHelper helper) {
        fill(helper, Blocks.STONE, 5, 9, 1, 5, 9);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_IRON.get(), o -> o.random.set(true)), new BlockPos(7, 2, 0), 0, 0);
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 64));

        useWandOn(helper, player, LARGE_CENTER, Direction.UP);

        int stone = countInLayer(helper, Blocks.STONE, 2);
        int cobble = countInLayer(helper, Blocks.COBBLESTONE, 2);
        helper.assertValueEqual(stone + cobble, 25, "blocks placed in random mode");
        // With 25 random picks from two items, the chance of only one type is about 6 in 100 million.
        helper.assertTrue(stone > 0 && cobble > 0, "Random mode should mix stone and cobblestone, got " + stone + "/" + cobble);
        helper.assertValueEqual(countItem(player, Items.STONE) + countItem(player, Items.COBBLESTONE), 128 - 25, "items used");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void offhandBlockOverridesTarget(GameTestHelper helper) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        assertLayer(helper, Blocks.COBBLESTONE, 2);
        helper.assertValueEqual(countItem(player, Items.COBBLESTONE), 64 - 9, "cobblestone used from the off hand");
        helper.assertValueEqual(countItem(player, Items.STONE), 64, "stone must not be used");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void noBlocksPlacedWithoutSupply(GameTestHelper helper) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), 0, "blocks placed with an empty inventory");
        helper.succeed();
    }

    // Server config limits

    @GameTest(template = LARGE)
    public static void undoHistoryKeepsThreeOperations(GameTestHelper helper) {
        fill(helper, Blocks.STONE, 6, 8, 1, 6, 8);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()), new BlockPos(7, 2, 0), 0, 0);
        giveStacks(player, Items.STONE, 64);

        for(int y = 1; y <= 4; y++) useWandOn(helper, player, new BlockPos(7, y, 7), Direction.UP);
        for(int y = 2; y <= 5; y++) helper.assertValueEqual(countInLayer(helper, Blocks.STONE, y), 9, "layer " + y + " placed");

        var history = ConstructionWand.instance.undoHistory;
        history.updateClient(player, true);
        for(int y = 5; y >= 3; y--) {
            helper.assertTrue(history.undo(player, helper.getLevel(), helper.absolutePos(new BlockPos(7, y, 7))), "Undo of layer " + y);
        }
        helper.assertFalse(history.undo(player, helper.getLevel(), helper.absolutePos(new BlockPos(7, 2, 7))),
                "A fourth undo must fail with the default history of 3");
        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), 9, "oldest layer kept");
        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 3), 0, "undone layer 3");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void maxRangeBlocksDistantPlacement(GameTestHelper helper) {
        buildFloor(helper);
        int range = ConfigServer.MAX_RANGE.get();
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()), new BlockPos(4 + range + 50, 2, 4), 0, 0);
        giveStacks(player, Items.STONE, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), 0, "blocks placed beyond MaxRange " + range);
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void blockEntityBlacklistIsRespected(GameTestHelper helper) {
        fill(helper, Blocks.BARREL, 3, 5, 1, 3, 5);
        List<?> original = ConfigServer.TE_LIST.get();
        try {
            // Test bodies run within one server tick, so the temporary config change cannot leak into other tests.
            ConfigServer.TE_LIST.set(List.of("minecraft:barrel"));
            FakePlayer blocked = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
            giveStacks(blocked, Items.BARREL, 64);
            useWandOn(helper, blocked, SMALL_CENTER, Direction.UP);
            helper.assertValueEqual(countInLayer(helper, Blocks.BARREL, 2), 0, "blacklisted barrels placed");

            ConfigServer.TE_LIST.set(original);
            FakePlayer allowed = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
            giveStacks(allowed, Items.BARREL, 64);
            useWandOn(helper, allowed, SMALL_CENTER, Direction.UP);
            helper.assertValueEqual(countInLayer(helper, Blocks.BARREL, 2), 9, "barrels placed with the default list");
        } finally {
            ConfigServer.TE_LIST.set(original);
        }
        helper.succeed();
    }

    // Recipes

    @GameTest(template = SMALL)
    public static void everyWandAndCoreHasARecipe(GameTestHelper helper) {
        for(var item : List.of(ModItems.WAND_STONE, ModItems.WAND_IRON, ModItems.WAND_DIAMOND, ModItems.WAND_INFINITY,
                ModItems.CORE_ANGEL, ModItems.CORE_DESTRUCTION)) {
            ResourceLocation id = item.getId();
            Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager().byKey(id);
            if(craftTweakerLoaded() && item == ModItems.WAND_STONE) {
                helper.assertFalse(recipe.isPresent(), "CraftTweaker sample script should remove " + id);
                continue;
            }
            helper.assertTrue(recipe.isPresent(), "Missing recipe " + id);
            ItemStack result = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(item.get()), "Recipe " + id + " makes " + result);
        }
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void everyUpgradeableWandAcceptsBothCores(GameTestHelper helper) {
        if(craftTweakerLoaded()) {
            CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(ModItems.WAND_IRON.get()), new ItemStack(ModItems.CORE_ANGEL.get())));
            helper.assertFalse(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isPresent(),
                    "CraftTweaker sample script should remove the upgrade recipe");
            helper.succeed();
            return;
        }
        for(Item wand : List.of(ModItems.WAND_IRON.get(), ModItems.WAND_DIAMOND.get(), ModItems.WAND_INFINITY.get())) {
            for(Item core : List.of(ModItems.CORE_ANGEL.get(), ModItems.CORE_DESTRUCTION.get())) {
                ItemStack result = craftUpgrade(helper, new ItemStack(wand), core);
                helper.assertTrue(new WandOptions(result).cores.getUpgrades().contains(core), wand + " should accept " + core);
            }
        }
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void sameCoreCannotBeInstalledTwice(GameTestHelper helper) {
        ItemStack upgraded = wandWithCore(ModItems.WAND_IRON.get(), ModItems.CORE_ANGEL.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(upgraded, new ItemStack(ModItems.CORE_ANGEL.get())));
        boolean matches = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isPresent();
        helper.assertFalse(matches, "Installing the same core twice should not match");
        helper.succeed();
    }

    // Integrations

    @GameTest(template = SMALL)
    public static void craftTweakerScriptChangesRecipes(GameTestHelper helper) {
        // Pack makers change recipes with CraftTweaker or KubeJS. src/crafttweaker/scripts/constructionwand_test.zs adds
        // stick + cobblestone -> stone wand and removes the upgrade recipe; without CraftTweaker neither change exists.
        boolean crafttweaker = craftTweakerLoaded();
        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.COBBLESTONE)));
        var easy = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertValueEqual(easy.isPresent(), crafttweaker, "stick + cobblestone recipe present");
        if(crafttweaker) {
            ItemStack result = easy.get().value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ModItems.WAND_STONE.get()), "Script recipe should make a stone wand, made " + result);
        }
        boolean upgrade = helper.getLevel().getRecipeManager().byKey(ConstructionWand.loc("dynamic/wand_upgrade")).isPresent();
        helper.assertValueEqual(upgrade, !crafttweaker, "upgrade recipe present");
        helper.succeed();
    }

    @GameTest(template = SMALL)
    public static void wandTakesBlocksFromSophisticatedBackpack(GameTestHelper helper) {
        // Confirmed design: like shulker boxes and bundles, backpacks in the inventory supply blocks.
        if(!ModList.get().isLoaded("sophisticatedbackpacks")) {
            helper.succeed();
            return;
        }
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
        player.getInventory().setItem(1, backpack);
        // Sophisticated Backpacks only exposes contents once the backpack has been used (it assigns a contents UUID
        // on first access, as when a player first opens it). Simulate that first use.
        initializeSophisticatedBackpack(player.getInventory().getItem(1));
        IItemHandler storage = player.getInventory().getItem(1).getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(storage != null, "Backpack should expose an item handler capability");
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(storage, new ItemStack(Items.STONE, 32), false);
        helper.assertTrue(leftover.isEmpty(), "Backpack should accept 32 stone (slots=" + storage.getSlots()
                + ", leftover=" + leftover.getCount() + ", handler=" + storage.getClass().getSimpleName() + ")");

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        assertLayer(helper, Blocks.STONE, 2);
        int left = 0;
        IItemHandler after = player.getInventory().getItem(1).getCapability(Capabilities.ItemHandler.ITEM);
        for(int i = 0; i < after.getSlots(); i++) if(after.getStackInSlot(i).is(Items.STONE)) left += after.getStackInSlot(i).getCount();
        helper.assertValueEqual(left, 32 - 9, "stone left in the backpack");
        helper.succeed();
    }

    private static boolean craftTweakerLoaded() {
        return ModList.get().isLoaded("crafttweaker");
    }

    /** Reflection keeps Sophisticated Backpacks a runtime-only test dependency. */
    private static void initializeSophisticatedBackpack(ItemStack backpack) {
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Object wrapper = wrapperClass.getMethod("fromStack", ItemStack.class).invoke(null, backpack);
            wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
        } catch(ReflectiveOperationException e) {
            throw new AssertionError("Could not initialize the Sophisticated Backpacks backpack", e);
        }
    }

    // Shared assertions

    private static void assertPlacementLimit(GameTestHelper helper, Item wandItem, int expected) {
        fill(helper, Blocks.STONE, 1, 13, 1, 1, 13);
        ItemStack wand = new ItemStack(wandItem);
        FakePlayer player = survivalPlayer(helper, wand, new BlockPos(7, 2, 0), 0, 0);
        giveStacks(player, Items.STONE, 192);

        useWandOn(helper, player, LARGE_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), expected, "blocks placed by " + wandItem);
        helper.assertValueEqual(wand.getDamageValue(), expected, "durability used by " + wandItem);
        helper.assertValueEqual(countItem(player, Items.STONE), 192 - expected, "stone used by " + wandItem);
        helper.succeed();
    }

    private static void assertDestructionLimit(GameTestHelper helper, Item wandItem, int expected) {
        fill(helper, Blocks.STONE, 1, 13, 1, 1, 13);
        FakePlayer player = survivalPlayer(helper, wandWithCore(wandItem, ModItems.CORE_DESTRUCTION.get()), new BlockPos(7, 2, 0), 0, 0);

        useWandOn(helper, player, LARGE_CENTER, Direction.UP);

        helper.assertValueEqual(169 - countInLayer(helper, Blocks.STONE, 1), expected, "blocks destroyed by " + wandItem);
        helper.assertBlockPresent(Blocks.AIR, LARGE_CENTER);
        helper.succeed();
    }

    private static void assertFloorLock(GameTestHelper helper, WandOptions.LOCK lock, int expected, Axis axis) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_STONE.get(), o -> o.lock.set(lock)));
        giveStacks(player, Items.STONE, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), expected, "blocks placed with lock " + lock);
        if(axis != null) {
            for(int i = -1; i <= 1; i++) {
                BlockPos pos = SMALL_CENTER.above().relative(axis, i);
                helper.assertBlockPresent(Blocks.STONE, pos);
            }
        }
        helper.succeed();
    }

    private static void assertWallLock(GameTestHelper helper, WandOptions.LOCK lock, Axis axis) {
        // 3x3 wall at z=6, clicked on its south face from the south.
        fill(helper, Blocks.STONE, 3, 5, 1, 6, 6);
        fill(helper, Blocks.STONE, 3, 5, 2, 6, 6);
        fill(helper, Blocks.STONE, 3, 5, 3, 6, 6);
        BlockPos center = new BlockPos(4, 2, 6);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_STONE.get(), o -> o.lock.set(lock)), new BlockPos(4, 1, 8), 180, 0);
        giveStacks(player, Items.STONE, 64);

        useWandOn(helper, player, center, Direction.SOUTH);

        int placed = 0;
        for(int x = 3; x <= 5; x++) for(int y = 1; y <= 3; y++) {
            if(helper.getBlockState(new BlockPos(x, y, 7)).is(Blocks.STONE)) placed++;
        }
        helper.assertValueEqual(placed, 3, "blocks placed on the wall with lock " + lock);
        for(int i = -1; i <= 1; i++) helper.assertBlockPresent(Blocks.STONE, center.south().relative(axis, i));
        helper.succeed();
    }

    private static void assertLogAxis(GameTestHelper helper, WandOptions.DIRECTION direction, Axis expected) {
        fill(helper, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X), 3, 5, 1, 3, 5);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_STONE.get(), o -> o.direction.set(direction)));
        giveStacks(player, Items.OAK_LOG, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        assertLayer(helper, Blocks.OAK_LOG, 2);
        for(int x = 3; x <= 5; x++) for(int z = 3; z <= 5; z++) {
            BlockState placed = helper.getBlockState(new BlockPos(x, 2, z));
            helper.assertValueEqual(placed.getValue(RotatedPillarBlock.AXIS), expected, "log axis with direction " + direction);
        }
        helper.succeed();
    }

    private static void assertMatch(GameTestHelper helper, WandOptions.MATCH match, Block center, Block ring, Item supply, int expected) {
        fill(helper, ring, 3, 5, 1, 3, 5);
        helper.setBlock(SMALL_CENTER, center);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_STONE.get(), o -> o.match.set(match)));
        giveStacks(player, supply, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        Block placedBlock = Block.byItem(supply);
        helper.assertValueEqual(countInLayer(helper, placedBlock, 2), expected, "blocks placed with match " + match);
        helper.assertBlockPresent(placedBlock, SMALL_CENTER.above());
        helper.succeed();
    }

    private static void assertReplace(GameTestHelper helper, boolean replace, int expected) {
        buildFloor(helper);
        BlockPos snow = new BlockPos(3, 2, 3);
        helper.setBlock(snow, Blocks.SNOW);
        FakePlayer player = survivalPlayer(helper, wand(ModItems.WAND_STONE.get(), o -> o.replace.set(replace)));
        giveStacks(player, Items.STONE, 64);

        useWandOn(helper, player, SMALL_CENTER, Direction.UP);

        helper.assertValueEqual(countInLayer(helper, Blocks.STONE, 2), expected, "blocks placed with replace " + replace);
        helper.assertBlockPresent(replace ? Blocks.STONE : Blocks.SNOW, snow);
        helper.succeed();
    }

    private static ItemStack craftUpgrade(GameTestHelper helper, ItemStack wand, Item core) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(wand, new ItemStack(core)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Upgrade recipe should match " + wand + " + " + core);
        return recipe.get().value().assemble(input, helper.getLevel().registryAccess());
    }
}
