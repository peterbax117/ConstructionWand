package thetadev.constructionwand;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thetadev.constructionwand.basics.ConfigClient;
import thetadev.constructionwand.basics.ConfigServer;
import thetadev.constructionwand.basics.ModStats;
import thetadev.constructionwand.client.ClientEvents;
import thetadev.constructionwand.client.ClientModEvents;
import thetadev.constructionwand.client.RenderBlockPreview;
import thetadev.constructionwand.containers.ContainerManager;
import thetadev.constructionwand.containers.ContainerRegistrar;
import thetadev.constructionwand.items.ModItems;
import thetadev.constructionwand.network.ModMessages;
import thetadev.constructionwand.wand.undo.UndoHistory;


@Mod(ConstructionWand.MODID)
public class ConstructionWand {
    public static final String MODID = "constructionwand";
    public static final String MODNAME = "ConstructionWand";

    public static ConstructionWand instance;
    public static final Logger LOGGER = LogManager.getLogger();

    public ContainerManager containerManager;
    public UndoHistory undoHistory;
    public RenderBlockPreview renderBlockPreview;

    // NeoForge passes the mod event bus and container into the constructor instead of FMLJavaModLoadingContext.
    public ConstructionWand(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;

        containerManager = new ContainerManager();
        undoHistory = new UndoHistory();

        modEventBus.addListener(this::commonSetup);
        if(FMLEnvironment.dist.isClient()) modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ModMessages::register);

        ModItems.ITEMS.register(modEventBus);
        ModItems.RECIPE_SERIALIZERS.register(modEventBus);
        ModStats.STATS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ConfigServer.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigClient.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ConstructionWand says hello - may the odds be ever in your favor.");

        ContainerRegistrar.register();
        event.enqueueWork(ModStats::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        renderBlockPreview = new RenderBlockPreview();
        NeoForge.EVENT_BUS.register(renderBlockPreview);
        NeoForge.EVENT_BUS.register(new ClientEvents());

        event.enqueueWork(ClientModEvents::registerModelProperties);
    }

    public static ResourceLocation loc(String name) {
        return ResourceLocation.fromNamespaceAndPath(MODID, name);
    }
}


