package thetadev.constructionwand.clienttest;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.ClientHooks;
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
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Client-side tests. They drive the real input paths where the game allows it: the sneak key mapping,
 * vanilla use/attack key mappings, the keyboard handler for the option key and NeoForge's scroll hook.
 * The physical option key state is the one thing that has to be simulated, via ClientEvents.keyStateSource.
 *
 * The player stands at {@link #STAND} in a creative superflat world, facing north towards a test wall.
 */
public final class ClientTests
{
    public record Case(String name, Consumer<ClientTestHarness> body) {}

    private static final BlockPos STAND = new BlockPos(0, -60, 0);
    private static final int WALL_Z = -3;
    private static final IntPredicate PHYSICAL_KEYS = ClientEvents.keyStateSource;

    public static List<Case> all() {
        return List.of(
                new Case("tooltips render for wand and core", ClientTests::tooltips),
                new Case("sneak + option key + right click opens the options screen", ClientTests::optionsScreen),
                new Case("every options screen button updates the server", ClientTests::everyOptionButton),
                new Case("sneak + scroll changes the direction lock", ClientTests::scrollChangesLock),
                new Case("sneak + left click switches the core and tints the wand", ClientTests::leftClickSwitchesCore),
                new Case("placement preview, undo preview and undo", ClientTests::placeAndUndo)
        );
    }

    // Test cases

    private static void tooltips(ClientTestHarness h) {
        h.onClient(() -> {
            Minecraft mc = h.mc();
            Item.TooltipContext context = Item.TooltipContext.of(mc.level);
            List<Component> wandLines = new ItemStack(ModItems.WAND_IRON.get()).getTooltipLines(context, mc.player, TooltipFlag.Default.NORMAL);
            check(containsKey(wandLines, "constructionwand.tooltip.blocks"), "Wand tooltip should show the block limit: " + wandLines);

            List<Component> coreLines = new ItemStack(ModItems.CORE_ANGEL.get()).getTooltipLines(context, mc.player, TooltipFlag.Default.NORMAL);
            check(containsKey(coreLines, "constructionwand.option.cores.constructionwand:core_angel.desc"),
                    "Core tooltip should show its description: " + coreLines);
        });
    }

    private static void optionsScreen(ClientTestHarness h) {
        prepare(h, () -> new ItemStack(ModItems.WAND_IRON.get()));
        look(h, -90);

        // With the default config, sneak alone must not open the screen.
        sneak(h, true);
        h.onClient(() -> KeyMapping.click(h.mc().options.keyUse.getKey()));
        h.waitTicks(10);
        check(h.onClient(() -> !(h.mc().screen instanceof ScreenWand)), "Sneak + right click without the option key opened the screen");

        optionKey(h, true);
        h.onClient(() -> KeyMapping.click(h.mc().options.keyUse.getKey()));
        h.await("options screen to open", 5, () -> h.mc().screen instanceof ScreenWand);

        // Buttons are created in the order cores, lock, direction, replace, match, random.
        h.onClient(() -> {
            List<Button> buttons = h.mc().screen.children().stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList();
            check(buttons.size() == 6, "Expected 6 option buttons, found " + buttons.size());
            Button lock = buttons.get(1);
            h.mc().screen.mouseClicked(lock.getX() + 5, lock.getY() + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        awaitServerLock(h, WandOptions.LOCK.HORIZONTAL);
    }

    private static void everyOptionButton(ClientTestHarness h) {
        prepare(h, () -> new ItemStack(ModItems.WAND_IRON.get()));
        look(h, -90);
        sneak(h, true);
        optionKey(h, true);
        h.onClient(() -> KeyMapping.click(h.mc().options.keyUse.getKey()));
        h.await("options screen to open", 5, () -> h.mc().screen instanceof ScreenWand);

        // Order: cores, lock, direction, replace, match, random. Click every option except lock (covered above).
        h.onClient(() -> {
            List<Button> buttons = h.mc().screen.children().stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList();
            check(!buttons.get(0).active, "Core button should be disabled when only the default core is installed");
            for(int i : new int[]{2, 3, 4, 5}) {
                Button button = buttons.get(i);
                h.mc().screen.mouseClicked(button.getX() + 5, button.getY() + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
        });
        h.await("server to apply direction, replace, match and random", 5, () -> h.onServer(p -> {
            WandOptions options = new WandOptions(p.getMainHandItem().copy());
            return options.direction.get() == WandOptions.DIRECTION.PLAYER
                    && !options.replace.get()
                    && options.match.get() == WandOptions.MATCH.ANY
                    && options.random.get();
        }));
    }

    private static void scrollChangesLock(ClientTestHarness h) {
        prepare(h, () -> new ItemStack(ModItems.WAND_IRON.get()));

        h.onClient(() -> ClientHooks.onMouseScroll(h.mc().mouseHandler, 0, -1));
        h.waitTicks(10);
        check(serverOptions(h).lock.get() == WandOptions.LOCK.NOLOCK, "Scrolling without sneak must not change the lock");

        sneak(h, true);
        h.onClient(() -> ClientHooks.onMouseScroll(h.mc().mouseHandler, 0, -1));
        awaitServerLock(h, WandOptions.LOCK.HORIZONTAL);
    }

    private static void leftClickSwitchesCore(ClientTestHarness h) {
        prepare(h, () -> {
            ItemStack wand = new ItemStack(ModItems.WAND_IRON.get());
            new WandOptions(wand).addUpgrade((IWandUpgrade) ModItems.CORE_ANGEL.get());
            return wand;
        });
        look(h, -90);
        check(h.onClient(() -> h.mc().getItemColors().getColor(h.mc().player.getMainHandItem(), 1)) == -1,
                "Wand without an active core should not be tinted");

        sneak(h, true);
        h.onClient(() -> KeyMapping.click(h.mc().options.keyAttack.getKey()));
        h.await("server to switch to the angel core", 5,
                () -> h.onServer(p -> new WandOptions(p.getMainHandItem()).cores.get() == ModItems.CORE_ANGEL.get()));

        h.onClient(() -> {
            ItemStack wand = h.mc().player.getMainHandItem();
            int tint = h.mc().getItemColors().getColor(wand, 1);
            int expected = FastColor.ARGB32.opaque(0xE9B115);
            check(tint == expected, String.format("Angel core tint should be %08X, was %08X", expected, tint));

            float usingCore = ItemProperties.getProperty(wand, ConstructionWand.loc("using_core"))
                    .call(wand, h.mc().level, h.mc().player, 0);
            check(usingCore == 1f, "using_core model property should be 1, was " + usingCore);
        });
    }

    private static void placeAndUndo(ClientTestHarness h) {
        prepare(h, () -> new ItemStack(ModItems.WAND_IRON.get()));
        h.onServerDo(p -> {
            for(int x = -1; x <= 1; x++) for(int y = -60; y <= -58; y++) {
                p.serverLevel().setBlockAndUpdate(new BlockPos(x, y, WALL_Z), Blocks.STONE.defaultBlockState());
            }
        });
        look(h, 0);
        BlockPos target = new BlockPos(0, -59, WALL_Z);
        h.await("crosshair on the test wall", 5,
                () -> h.mc().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target));

        Set<BlockPos> expected = new HashSet<>();
        for(int x = -1; x <= 1; x++) for(int y = -60; y <= -58; y++) expected.add(new BlockPos(x, y, WALL_Z + 1));

        // The normal placement preview is computed while the block highlight renders.
        h.await("placement preview to show 9 blocks", 5, () -> expected.equals(previewPositions(h)));

        h.onClient(() -> KeyMapping.click(h.mc().options.keyUse.getKey()));
        h.await("server to place 9 blocks", 5, () -> h.onServer(p -> expected.stream()
                .allMatch(pos -> p.serverLevel().getBlockState(pos).is(Blocks.STONE))));

        // Holding sneak + option key asks the server for the undo preview.
        sneak(h, true);
        optionKey(h, true);
        h.await("undo preview from the server", 5, () -> {
            Set<BlockPos> undo = ConstructionWand.instance.renderBlockPreview.undoBlocks;
            return undo != null && undo.equals(expected);
        });

        h.onClient(() -> KeyMapping.click(h.mc().options.keyUse.getKey()));
        h.await("server to undo the placement", 5, () -> h.onServer(p -> expected.stream()
                .allMatch(pos -> p.serverLevel().getBlockState(pos).isAir())));
    }

    // Helpers

    /** Clears the test area and inventory, gives the wand and places the player at the start position. */
    private static void prepare(ClientTestHarness h, Supplier<ItemStack> wand) {
        // Empty the hand first and wait for the client to see it, so a previous test's wand can't be mistaken for the new one.
        h.onServerDo(p -> {
            p.getInventory().clearContent();
            p.getInventory().selected = 0;
            p.inventoryMenu.broadcastChanges();
        });
        h.onClient(() -> h.mc().player.getInventory().selected = 0);
        h.await("client inventory to clear", 5, () -> h.mc().player.getMainHandItem().isEmpty());

        h.onServerDo(p -> {
            for(int x = -3; x <= 3; x++) for(int y = -60; y <= -56; y++) for(int z = -6; z <= 3; z++) {
                p.serverLevel().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            p.getInventory().setItem(0, wand.get());
            p.inventoryMenu.broadcastChanges();
            p.teleportTo(p.serverLevel(), STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5, 180f, 0f);
        });
        h.await("client to receive the wand", 5, () -> h.mc().player.getMainHandItem().is(ModItems.WAND_IRON.get()));
        h.waitTicks(10);
    }

    private static void look(ClientTestHarness h, float pitch) {
        h.onClient(() -> {
            h.mc().player.setYRot(180f);
            h.mc().player.setXRot(pitch);
        });
        h.waitTicks(5);
    }

    private static void sneak(ClientTestHarness h, boolean down) {
        h.onClient(() -> h.mc().options.keyShift.setDown(down));
        h.await("player crouch state " + down, 5, () -> h.mc().player.isCrouching() == down);
        h.waitTicks(3);
    }

    /** Presses the option key through the real keyboard handler, which fires InputEvent.Key. */
    private static void optionKey(ClientTestHarness h, boolean down) {
        h.onClient(() -> {
            int key = ConfigClient.OPT_KEY.get();
            ClientEvents.keyStateSource = down ? id -> id == key || PHYSICAL_KEYS.test(id) : PHYSICAL_KEYS;
            h.mc().keyboardHandler.keyPress(h.mc().getWindow().getWindow(), key, 0,
                    down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
        });
        h.waitTicks(3);
    }

    static void releaseAllInput(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        if(ClientEvents.keyStateSource != PHYSICAL_KEYS) {
            ClientEvents.keyStateSource = PHYSICAL_KEYS;
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), ConfigClient.OPT_KEY.get(), 0, GLFW.GLFW_RELEASE, 0);
        }
    }

    private static WandOptions serverOptions(ClientTestHarness h) {
        return h.onServer((ServerPlayer p) -> new WandOptions(p.getMainHandItem().copy()));
    }

    private static void awaitServerLock(ClientTestHarness h, WandOptions.LOCK expected) {
        h.await("server wand lock to become " + expected, 5,
                () -> h.onServer(p -> new WandOptions(p.getMainHandItem().copy()).lock.get() == expected));
    }

    private static Set<BlockPos> previewPositions(ClientTestHarness h) {
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

    private static void check(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
}
