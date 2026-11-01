package thetadev.constructionwand.clienttest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import thetadev.constructionwand.ConstructionWand;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Scripted client test runner, modeled on NeoForge's own SelfTestClient. It only activates when the
 * {@code constructionwand.clienttest.report} system property is set (the clientTest run sets it).
 * Tests run on a background thread and hop onto the client or integrated server thread for each action,
 * so they read top to bottom. The result is written to the report file and the client then exits.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID, value = Dist.CLIENT)
public final class ClientTestHarness
{
    private static final String REPORT_PROPERTY = "constructionwand.clienttest.report";
    private static final long OVERALL_TIMEOUT_MINUTES = 5;
    private static final AtomicLong CLIENT_TICKS = new AtomicLong();

    private final Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        String report = System.getProperty(REPORT_PROPERTY);
        if(report == null) return;

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> CLIENT_TICKS.incrementAndGet());
        Thread runner = new Thread(() -> new ClientTestHarness().runAll(Path.of(report)), "constructionwand-clienttest");
        runner.setDaemon(true);
        runner.start();
        startWatchdog(Path.of(report));
    }

    private void runAll(Path reportFile) {
        List<String> lines = new ArrayList<>();
        int failures = 0;
        try {
            enterTestWorld();
            for(ClientTests.Case testCase : ClientTests.all()) {
                try {
                    testCase.body().accept(this);
                    lines.add("  ok    " + testCase.name());
                } catch(Throwable t) {
                    failures++;
                    lines.add("  FAIL  " + testCase.name() + ": " + t);
                    ConstructionWand.LOGGER.error("Client test failed: {}", testCase.name(), t);
                } finally {
                    resetInput();
                }
            }
        } catch(Throwable t) {
            failures++;
            lines.add("  FAIL  setup: " + stackTrace(t));
        }

        String header = (failures == 0 ? "PASS" : "FAIL") + " " + (lines.size() - failures) + " passed, " + failures + " failed";
        writeReport(reportFile, header + System.lineSeparator() + String.join(System.lineSeparator(), lines));
        ConstructionWand.LOGGER.info("Client tests finished: {}", header);
        leaveWorldAndStop();
    }

    // World lifecycle

    private void enterTestWorld() {
        await("game finished loading", 180, () -> mc.getOverlay() == null && mc.screen != null);
        onClient(() -> {
            // A window that loses focus would otherwise open the pause menu and block input.
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            LevelSettings settings = new LevelSettings("clienttest-" + System.currentTimeMillis(), GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(0L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
        });
        await("player joined the test world", 180,
                () -> mc.player != null && mc.level != null && mc.screen == null && mc.getSingleplayerServer() != null);
        waitTicks(40);
    }

    private void leaveWorldAndStop() {
        try {
            onClient(() -> {
                if(mc.level != null) {
                    mc.level.disconnect();
                    mc.disconnect(new GenericMessageScreen(Component.literal("Client tests finished")));
                }
                mc.stop();
            });
        } catch(Throwable t) {
            Runtime.getRuntime().halt(1);
        }
    }

    private static void startWatchdog(Path reportFile) {
        Thread watchdog = new Thread(() -> {
            try {
                TimeUnit.MINUTES.sleep(OVERALL_TIMEOUT_MINUTES);
            } catch(InterruptedException e) {
                return;
            }
            if(!Files.exists(reportFile)) {
                writeReport(reportFile, "FAIL client tests did not finish within " + OVERALL_TIMEOUT_MINUTES + " minutes");
            }
            Runtime.getRuntime().halt(1);
        }, "constructionwand-clienttest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // Thread helpers used by the tests

    public <T> T onClient(Supplier<T> action) {
        try {
            return mc.submit(action).get(30, TimeUnit.SECONDS);
        } catch(Exception e) {
            throw unwrap("Client action failed", e);
        }
    }

    public void onClient(Runnable action) {
        onClient(() -> {
            action.run();
            return null;
        });
    }

    public <T> T onServer(Function<ServerPlayer, T> action) {
        var server = mc.getSingleplayerServer();
        if(server == null) throw new IllegalStateException("No integrated server");
        try {
            return server.submit(() -> action.apply(server.getPlayerList().getPlayers().get(0))).get(30, TimeUnit.SECONDS);
        } catch(Exception e) {
            throw unwrap("Server action failed", e);
        }
    }

    public void onServerDo(Consumer<ServerPlayer> action) {
        onServer(player -> {
            action.accept(player);
            return null;
        });
    }

    public void waitTicks(int ticks) {
        long target = CLIENT_TICKS.get() + ticks;
        await(ticks + " client ticks", 60, () -> CLIENT_TICKS.get() >= target);
    }

    /** Polls a condition on the client thread until it holds or the timeout expires. */
    public void await(String description, int timeoutSeconds, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while(System.nanoTime() < deadline) {
            if(onClient(condition::getAsBoolean)) return;
            try {
                Thread.sleep(50);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out after " + timeoutSeconds + "s waiting for: " + description);
    }

    public Minecraft mc() {
        return mc;
    }

    private void resetInput() {
        try {
            onClient(() -> {
                ClientTests.releaseAllInput(mc);
                if(mc.screen != null) mc.setScreen(null);
            });
            waitTicks(5);
        } catch(Throwable ignored) {
        }
    }

    // Rethrows assertion failures from the client or server thread as-is, so the report shows the real message.
    private static RuntimeException unwrap(String context, Exception e) {
        Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
        if(cause instanceof AssertionError assertion) throw assertion;
        if(cause instanceof RuntimeException runtime) return runtime;
        return new RuntimeException(context, cause);
    }

    private static void writeReport(Path reportFile, String content) {
        try {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, content + System.lineSeparator());
        } catch(IOException e) {
            ConstructionWand.LOGGER.error("Could not write client test report", e);
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
