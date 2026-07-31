package decok.dfcdvadstf.modernresourcepack;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.modernresourcepack.utils.handlers.WorldResourcePackEventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION)
public class ModernResourcePackSelectingUI {
    public static Logger logger = LogManager.getLogger(Tags.NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("Initializing ModernResourcePackUI Mod");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // World-scoped pack only makes sense client-side — it reads the local save folder.
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            FMLCommonHandler.instance().bus().register(new WorldResourcePackEventHandler());
            logger.info("Registered world-scoped resource pack handler");
        }
    }
}

