package decok.dfcdvadstf.modernresourcepack.utils.handlers;

import java.io.File;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.modernresourcepack.api.WorldResourcePackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldServer;

/**
 * Hooks the client into the world-scoped resource pack lifecycle.
 *
 * On single-player connect, we probe the save folder for {@code resources.zip} and
 * activate the pack if it's there. On disconnect we always tear down — it's cheap and
 * keeps the client clean whether the world had an opt-in pack or not.
 *
 * Multiplayer is skipped entirely: there's no local save folder to read from, and by
 * design the feature is save-bound.
 */
@SideOnly(Side.CLIENT)
public class WorldResourcePackEventHandler {

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return; // multiplayer — bail

        File saveDir = resolveSaveFolder(server);
        if (saveDir == null) return;

        if (WorldResourcePackManager.activateFromSaveFolder(saveDir)) {
            // Defer the reload — we're mid-connection, doing it synchronously could fight
            // the pipeline that Minecraft is already setting up for this world.
            WorldResourcePackManager.scheduleRefresh();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (WorldResourcePackManager.deactivate()) {
            WorldResourcePackManager.scheduleRefresh();
        }
    }

    /** Locate dim-0 save root. Returns {@code null} if the integrated server hasn't fully spun up. */
    private static File resolveSaveFolder(IntegratedServer server) {
        try {
            WorldServer ws = server.worldServerForDimension(0);
            if (ws == null || ws.getSaveHandler() == null) return null;
            return ws.getSaveHandler().getWorldDirectory();
        } catch (Throwable t) {
            return null;
        }
    }
}
