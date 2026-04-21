package decok.dfcdvadstf.modernresourcepack;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
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
}
