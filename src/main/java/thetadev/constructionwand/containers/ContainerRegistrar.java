package thetadev.constructionwand.containers;

import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.containers.handlers.HandlerBundle;
import thetadev.constructionwand.containers.handlers.HandlerCapability;
import thetadev.constructionwand.containers.handlers.HandlerShulkerbox;

public class ContainerRegistrar
{
    public static void register() {
        ConstructionWand.instance.containerManager.register(new HandlerCapability());
        ConstructionWand.instance.containerManager.register(new HandlerShulkerbox());
        ConstructionWand.instance.containerManager.register(new HandlerBundle());
        // Botania integration removed: Botania has no 1.21.1 release yet.
    }
}
