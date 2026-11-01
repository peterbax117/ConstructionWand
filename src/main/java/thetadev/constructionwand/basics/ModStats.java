package thetadev.constructionwand.basics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.registries.DeferredRegister;
import thetadev.constructionwand.ConstructionWand;

public class ModStats
{
    public static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, ConstructionWand.MODID);

    public static final ResourceLocation USE_WAND = ConstructionWand.loc("use_wand");

    static {
        STATS.register(USE_WAND.getPath(), () -> USE_WAND);
    }

    // Registries are frozen after startup in NeoForge, so the stat itself is registered via STATS.
    // This only assigns the display formatter, mirroring net.minecraft.stats.Stats#makeCustomStat.
    public static void register() {
        Stats.CUSTOM.get(USE_WAND, StatFormatter.DEFAULT);
    }
}
