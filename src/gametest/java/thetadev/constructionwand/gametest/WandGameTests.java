package thetadev.constructionwand.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandUpgrade;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.network.PacketUndoBlocks;
import thetadev.constructionwand.network.PacketWandOption;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side GameTests for Construction Wand. Run headless with {@code gradlew runGameTestServer}.
 * Each test uses an empty 9x5x9 structure and builds its own fixture, usually a 3x3 stone floor at y=1.
 */
@GameTestHolder(ConstructionWand.MODID)
@PrefixGameTestTemplate(false)
public class WandGameTests
{
    private static final String TEMPLATE = "empty_9x5x9";
    private static final BlockPos FLOOR_CENTER = new BlockPos(4, 1, 4);

    // Wand placement and block supply

    @GameTest(template = TEMPLATE)
    public static void stoneWandExtendsFloor(GameTestHelper helper) {
        buildFloor(helper);
        ItemStack wand = new ItemStack(ModItems.WAND_STONE.get());
        FakePlayer player = survivalPlayer(helper, wand);
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));

        InteractionResult result = useWandOn(helper, player, FLOOR_CENTER, Direction.UP);

        helper.assertTrue(result.consumesAction(), "Wand use should succeed, got " + result);
        assertLayer(helper, Blocks.STONE, 2);
        helper.assertValueEqual(countItem(player, Items.STONE), 64 - 9, "stone left in inventory");
        helper.assertValueEqual(wand.getDamageValue(), 9, "wand damage");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wandRespectsBlockLimit(GameTestHelper helper) {
        // A 5x5 floor has room for 25 blocks, but the stone wand's default limit is 9.
        for(int x = 2; x <= 6; x++) for(int z = 2; z <= 6; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));

        useWandOn(helper, player, FLOOR_CENTER, Direction.UP);

        int placed = 0;
        for(int x = 2; x <= 6; x++) for(int z = 2; z <= 6; z++) {
            if(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.STONE)) placed++;
        }
        helper.assertValueEqual(placed, 9, "blocks placed by stone wand");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wandTakesBlocksFromShulkerBox(GameTestHelper helper) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, 32))));
        player.getInventory().setItem(1, box);

        useWandOn(helper, player, FLOOR_CENTER, Direction.UP);

        assertLayer(helper, Blocks.STONE, 2);
        int left = player.getInventory().getItem(1).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyStream().mapToInt(ItemStack::getCount).sum();
        helper.assertValueEqual(left, 32 - 9, "stone left in shulker box");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wandTakesBlocksFromBundle(GameTestHelper helper) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.STONE, 16))));
        player.getInventory().setItem(1, bundle);

        useWandOn(helper, player, FLOOR_CENTER, Direction.UP);

        assertLayer(helper, Blocks.STONE, 2);
        int left = 0;
        for(ItemStack stack : player.getInventory().getItem(1).getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemsCopy()) {
            left += stack.getCount();
        }
        helper.assertValueEqual(left, 16 - 9, "stone left in bundle");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void undoRemovesPlacedBlocks(GameTestHelper helper) {
        buildFloor(helper);
        FakePlayer player = survivalPlayer(helper, new ItemStack(ModItems.WAND_STONE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));
        useWandOn(helper, player, FLOOR_CENTER, Direction.UP);
        assertLayer(helper, Blocks.STONE, 2);

        // Simulates the client holding the undo key, which normally arrives as a PacketQueryUndo.
        ConstructionWand.instance.undoHistory.updateClient(player, true);
        boolean undone = ConstructionWand.instance.undoHistory.undo(player, helper.getLevel(), helper.absolutePos(FLOOR_CENTER.above()));

        helper.assertTrue(undone, "Undo should succeed");
        assertLayer(helper, Blocks.AIR, 2);
        helper.assertValueEqual(countItem(player, Items.STONE), 64, "stone returned to inventory");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destructionCoreRemovesLayer(GameTestHelper helper) {
        buildFloor(helper);
        ItemStack wand = new ItemStack(ModItems.WAND_DIAMOND.get());
        WandOptions options = new WandOptions(wand);
        options.addUpgrade((IWandUpgrade) ModItems.CORE_DESTRUCTION.get());
        options.cores.setValueString(ConstructionWand.loc("core_destruction").toString());
        FakePlayer player = survivalPlayer(helper, wand);

        useWandOn(helper, player, FLOOR_CENTER, Direction.UP);

        assertLayer(helper, Blocks.AIR, 1);
        helper.succeed();
    }

    // Crafting

    @GameTest(template = TEMPLATE)
    public static void upgradeRecipeAddsCore(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.WAND_IRON.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(wand, new ItemStack(ModItems.CORE_ANGEL.get())));

        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Upgrade recipe should be loaded and match iron wand + angel core");
        helper.assertValueEqual(recipe.get().id(), ConstructionWand.loc("dynamic/wand_upgrade"), "recipe id");

        ItemStack result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(new WandOptions(result).cores.getUpgrades().contains(ModItems.CORE_ANGEL.get()),
                "Upgraded wand should contain the angel core");
        helper.assertValueEqual(new WandOptions(wand).cores.getUpgrades().size(), 1, "cores on the unmodified input wand");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stoneWandIsNotUpgradeable(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(2, 1,
                List.of(new ItemStack(ModItems.WAND_STONE.get()), new ItemStack(ModItems.CORE_ANGEL.get())));
        boolean matches = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isPresent();
        helper.assertFalse(matches, "Stone wand is configured as not upgradeable");
        helper.succeed();
    }

    // Data components and networking

    @GameTest(template = TEMPLATE)
    public static void wandOptionsPersistInCustomData(GameTestHelper helper) {
        ItemStack wand = new ItemStack(ModItems.WAND_IRON.get());
        new WandOptions(wand).lock.set(WandOptions.LOCK.HORIZONTAL);
        new WandOptions(wand).random.set(true);

        WandOptions reloaded = new WandOptions(wand);
        helper.assertValueEqual(reloaded.lock.get(), WandOptions.LOCK.HORIZONTAL, "lock option");
        helper.assertValueEqual(reloaded.random.get(), true, "random option");

        CompoundTag stored = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("wand_options");
        helper.assertValueEqual(stored.getString("lock"), "horizontal", "stored lock value");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void legacyWandOptionsAreRead(GameTestHelper helper) {
        // Shape of the data after vanilla moves a 1.20 wand's NBT into minecraft:custom_data.
        CompoundTag options = new CompoundTag();
        ListTag cores = new ListTag();
        cores.add(StringTag.valueOf("constructionwand:core_angel"));
        options.put("cores", cores);
        options.putByte("cores_sel", (byte) 1);
        CompoundTag root = new CompoundTag();
        root.put("wand_options", options);

        ItemStack wand = new ItemStack(ModItems.WAND_DIAMOND.get());
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        helper.assertValueEqual(new WandOptions(wand).cores.get(), (Object) ModItems.CORE_ANGEL.get(), "selected core");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void packetsSurviveEncoding(GameTestHelper helper) {
        PacketWandOption option = new PacketWandOption("lock", "horizontal", true);
        helper.assertValueEqual(roundTrip(PacketWandOption.STREAM_CODEC, option), option, "PacketWandOption");

        PacketUndoBlocks undo = new PacketUndoBlocks(Set.of(new BlockPos(1, 2, 3), new BlockPos(-4, 70, 9)));
        helper.assertValueEqual(roundTrip(PacketUndoBlocks.STREAM_CODEC, undo), undo, "PacketUndoBlocks");
        helper.succeed();
    }

    // Helpers

    private static FakePlayer survivalPlayer(GameTestHelper helper, ItemStack wand) {
        // A unique profile per test keeps undo history and inventories from leaking between tests.
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wand-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        Vec3 standing = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 0)));
        player.moveTo(standing.x, standing.y, standing.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static void buildFloor(GameTestHelper helper) {
        for(int x = 3; x <= 5; x++) for(int z = 3; z <= 5; z++) helper.setBlock(x, 1, z, Blocks.STONE);
    }

    private static InteractionResult useWandOn(GameTestHelper helper, Player player, BlockPos relative, Direction face) {
        BlockPos pos = helper.absolutePos(relative);
        Vec3 hitLocation = Vec3.atCenterOf(pos).relative(face, 0.5);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hitLocation, face, pos, false));
        return player.getMainHandItem().useOn(context);
    }

    private static void assertLayer(GameTestHelper helper, Block block, int y) {
        for(int x = 3; x <= 5; x++) for(int z = 3; z <= 5; z++) helper.assertBlockPresent(block, new BlockPos(x, y, z));
    }

    private static int countItem(Player player, Item item) {
        int total = 0;
        for(ItemStack stack : player.getInventory().items) if(stack.is(item)) total += stack.getCount();
        return total;
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, value);
        return codec.decode(buffer);
    }
}
