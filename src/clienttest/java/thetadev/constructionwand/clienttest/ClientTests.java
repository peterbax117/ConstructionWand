package thetadev.constructionwand.clienttest;

import io.github.peterbax117.modtestharness.client.ClientTest;
import io.github.peterbax117.modtestharness.client.ClientTestContext;
import io.github.peterbax117.modtestharness.client.ClientTestHolder;
import io.github.peterbax117.modtestharness.client.SimulatedKeys;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.api.IWandUpgrade;
import thetadev.constructionwand.basics.ConfigClient;
import thetadev.constructionwand.basics.option.WandOptions;
import thetadev.constructionwand.client.ClientEvents;
import thetadev.constructionwand.client.ScreenWand;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.wand.WandJob;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static io.github.peterbax117.modtestharness.client.ClientTestContext.check;

/**
 * Client tests for Construction Wand, run by neoforge-mod-test-harness (gradlew clientTest).
 * The player stands at {@link #STAND} in a creative superflat world, facing north towards a test wall.
 */
@ClientTestHolder
public final class ClientTests
{
    private static final BlockPos STAND = new BlockPos(0, -60, 0);
    private static final int WALL_Z = -3;

    static {
        // ClientEvents polls the physical option key; let it also see keys held by the harness.
        IntPredicate physical = ClientEvents.keyStateSource;
        ClientEvents.keyStateSource = key -> SimulatedKeys.isDown(key) || physical.test(key);
    }

    @ClientTest(value = "tooltips render for wand and core", order = 1)
    public static void tooltips(ClientTestContext ctx) {
        ctx.onClient(() -> {
            var mc = ctx.mc();
            Item.TooltipContext context = Item.TooltipContext.of(mc.level);
            List<Component> wandLines = new ItemStack(ModItems.WAND_IRON.get()).getTooltipLines(context, mc.player, TooltipFlag.Default.NORMAL);
            check(containsKey(wandLines, "constructionwand.tooltip.blocks"), "Wand tooltip should show the block limit: " + wandLines);

            List<Component> coreLines = new ItemStack(ModItems.CORE_ANGEL.get()).getTooltipLines(context, mc.player, TooltipFlag.Default.NORMAL);
            check(containsKey(coreLines, "constructionwand.option.cores.constructionwand:core_angel.desc"),
                    "Core tooltip should show its description: " + coreLines);
        });
    }

    @ClientTest(value = "sneak + option key + right click opens the options screen", order = 2)
    public static void optionsScreen(ClientTestContext ctx) {
        prepare(ctx, () -> new ItemStack(ModItems.WAND_IRON.get()));
        ctx.look(180, -90);

        // With the default config, sneak alone must not open the screen.
        ctx.sneak(true);
        ctx.clickUse();
        ctx.waitTicks(10);
        check(ctx.onClient(() -> !(ctx.mc().screen instanceof ScreenWand)), "Sneak + right click without the option key opened the screen");

        optionKey(ctx, true);
        ctx.clickUse();
        ScreenWand screen = ctx.awaitScreen(ScreenWand.class);

        // Buttons are created in the order cores, lock, direction, replace, match, random.
        ctx.onClient(() -> {
            List<Button> buttons = buttons(screen);
            check(buttons.size() == 6, "Expected 6 option buttons, found " + buttons.size());
            Button lock = buttons.get(1);
            screen.mouseClicked(lock.getX() + 5, lock.getY() + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        awaitServerLock(ctx, WandOptions.LOCK.HORIZONTAL);
    }

    @ClientTest(value = "every options screen button updates the server", order = 3)
    public static void everyOptionButton(ClientTestContext ctx) {
        prepare(ctx, () -> new ItemStack(ModItems.WAND_IRON.get()));
        ctx.look(180, -90);
        ctx.sneak(true);
        optionKey(ctx, true);
        ctx.clickUse();
        ScreenWand screen = ctx.awaitScreen(ScreenWand.class);

        // Click every option except lock (covered above).
        ctx.onClient(() -> {
            List<Button> buttons = buttons(screen);
            check(!buttons.get(0).active, "Core button should be disabled when only the default core is installed");
            for(int i : new int[]{2, 3, 4, 5}) {
                Button button = buttons.get(i);
                screen.mouseClicked(button.getX() + 5, button.getY() + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
        });
        ctx.await("server to apply direction, replace, match and random", () -> ctx.onServer(p -> {
            WandOptions options = new WandOptions(p.getMainHandItem().copy());
            return options.direction.get() == WandOptions.DIRECTION.PLAYER
                    && !options.replace.get()
                    && options.match.get() == WandOptions.MATCH.ANY
                    && options.random.get();
        }));
    }

    @ClientTest(value = "sneak + scroll changes the direction lock", order = 4)
    public static void scrollChangesLock(ClientTestContext ctx) {
        prepare(ctx, () -> new ItemStack(ModItems.WAND_IRON.get()));

        ctx.scroll(-1);
        ctx.waitTicks(10);
        check(ctx.onServer(p -> new WandOptions(p.getMainHandItem().copy()).lock.get()) == WandOptions.LOCK.NOLOCK,
                "Scrolling without sneak must not change the lock");

        ctx.sneak(true);
        ctx.scroll(-1);
        awaitServerLock(ctx, WandOptions.LOCK.HORIZONTAL);
    }

    @ClientTest(value = "sneak + left click switches the core and tints the wand", order = 5)
    public static void leftClickSwitchesCore(ClientTestContext ctx) {
        prepare(ctx, () -> {
            ItemStack wand = new ItemStack(ModItems.WAND_IRON.get());
            new WandOptions(wand).addUpgrade((IWandUpgrade) ModItems.CORE_ANGEL.get());
            return wand;
        });
        ctx.look(180, -90);
        check(ctx.onClient(() -> ctx.mc().getItemColors().getColor(ctx.mc().player.getMainHandItem(), 1)) == -1,
                "Wand without an active core should not be tinted");

        ctx.sneak(true);
        ctx.clickAttack();
        ctx.await("server to switch to the angel core",
                () -> ctx.onServer(p -> new WandOptions(p.getMainHandItem()).cores.get() == ModItems.CORE_ANGEL.get()));

        ctx.onClient(() -> {
            ItemStack wand = ctx.mc().player.getMainHandItem();
            int tint = ctx.mc().getItemColors().getColor(wand, 1);
            int expected = FastColor.ARGB32.opaque(0xE9B115);
            check(tint == expected, String.format("Angel core tint should be %08X, was %08X", expected, tint));

            float usingCore = ItemProperties.getProperty(wand, ConstructionWand.loc("using_core"))
                    .call(wand, ctx.mc().level, ctx.mc().player, 0);
            check(usingCore == 1f, "using_core model property should be 1, was " + usingCore);
        });
    }

    @ClientTest(value = "placement preview, undo preview and undo", order = 6)
    public static void placeAndUndo(ClientTestContext ctx) {
        prepare(ctx, () -> new ItemStack(ModItems.WAND_IRON.get()));
        ctx.fill(new BlockPos(-1, -60, WALL_Z), new BlockPos(1, -58, WALL_Z), Blocks.STONE.defaultBlockState());
        ctx.look(180, 0);
        BlockPos target = new BlockPos(0, -59, WALL_Z);
        ctx.await("crosshair on the test wall",
                () -> ctx.mc().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target));

        Set<BlockPos> expected = new HashSet<>();
        for(int x = -1; x <= 1; x++) for(int y = -60; y <= -58; y++) expected.add(new BlockPos(x, y, WALL_Z + 1));

        // The normal placement preview is computed while the block highlight renders.
        ctx.await("placement preview to show 9 blocks", () -> expected.equals(previewPositions()));

        ctx.clickUse();
        ctx.await("server to place 9 blocks", () -> ctx.onServer(p -> expected.stream()
                .allMatch(pos -> p.serverLevel().getBlockState(pos).is(Blocks.STONE))));

        // Holding sneak + option key asks the server for the undo preview.
        ctx.sneak(true);
        optionKey(ctx, true);
        ctx.await("undo preview from the server", () -> expected.equals(ConstructionWand.instance.renderBlockPreview.undoBlocks));

        ctx.clickUse();
        ctx.await("server to undo the placement", () -> ctx.onServer(p -> expected.stream()
                .allMatch(pos -> p.serverLevel().getBlockState(pos).isAir())));
    }

    // Helpers

    /** Clears the test area, gives the wand and places the player at the start position facing north. */
    private static void prepare(ClientTestContext ctx, Supplier<ItemStack> wand) {
        ctx.fill(new BlockPos(-3, -60, -6), new BlockPos(3, -56, 3), Blocks.AIR.defaultBlockState());
        ctx.giveMainHand(wand);
        ctx.teleport(STAND, 180, 0);
    }

    private static void optionKey(ClientTestContext ctx, boolean down) {
        ctx.pressKey(ConfigClient.OPT_KEY.get(), down);
    }

    private static List<Button> buttons(ScreenWand screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static void awaitServerLock(ClientTestContext ctx, WandOptions.LOCK expected) {
        ctx.await("server wand lock to become " + expected,
                () -> ctx.onServer(p -> new WandOptions(p.getMainHandItem().copy()).lock.get() == expected));
    }

    private static Set<BlockPos> previewPositions() {
        try {
            Field field = ConstructionWand.instance.renderBlockPreview.getClass().getDeclaredField("wandJob");
            field.setAccessible(true);
            WandJob job = (WandJob) field.get(ConstructionWand.instance.renderBlockPreview);
            return job == null ? Set.of() : job.getBlockPositions();
        } catch(ReflectiveOperationException e) {
            throw new AssertionError("Cannot read the placement preview", e);
        }
    }

    private static boolean containsKey(List<Component> lines, String key) {
        for(Component line : lines) if(hasKey(line, key)) return true;
        return false;
    }

    private static boolean hasKey(Component component, String key) {
        if(component.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) return true;
        for(Component sibling : component.getSiblings()) if(hasKey(sibling, key)) return true;
        return false;
    }
}
