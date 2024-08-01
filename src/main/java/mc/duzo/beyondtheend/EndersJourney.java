package mc.duzo.beyondtheend;

import com.mojang.logging.LogUtils;
import com.mojang.math.Vector3d;
import mc.duzo.beyondtheend.data.global.server.ServerData;
import mc.duzo.beyondtheend.realm.RealmManager;
import mc.duzo.beyondtheend.sound.EnderSounds;
import mc.duzo.beyondtheend.world.dimension.EnderDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EndersJourney.MODID)
public class EndersJourney {

    public static final String MODID = "beyondtheend";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EndersJourney() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        EnderDimensions.initialise();
        EnderSounds.register(bus);

        bus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {}

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerData.get().getRealmManager().getPlayer().onJoin(event.getEntity());
    }
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerData.get().getRealmManager().getPlayer().onLeave(event.getEntity());
    }
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ServerData.get().getRealmManager().getPlayer().onRespawn(event.getEntity());
    }
    @SubscribeEvent
    public void onEntitySpawn(LivingSpawnEvent.CheckSpawn event) {
        if (RealmManager.isInRealm(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    public static MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static Vector3d getCentre(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
