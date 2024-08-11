package mc.duzo.beyondtheend.realm;

import com.mojang.math.Vector3d;
import mc.duzo.beyondtheend.EndersJourney;
import mc.duzo.beyondtheend.data.Savable;
import mc.duzo.beyondtheend.world.dimension.EnderDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RealmManager implements Savable {
	private RealmStructure structure;
	private PlayerManager player;

	public RealmManager() {}
	public RealmManager(CompoundTag data) {
		this.deserialise(data);
	}

	@Override
	public CompoundTag serialise() {
		CompoundTag data = new CompoundTag();

		data.put("Structure", this.getStructure().serialise());
		data.put("Player", this.getPlayer().serialise());

		return data;
	}

	@Override
	public void deserialise(CompoundTag data) {
		this.structure = new RealmStructure(data.getCompound("Structure"));
		this.player = new PlayerManager(this, data.getCompound("Player"));
	}

	public static boolean isInRealm(LivingEntity entity) {
		return EnderDimensions.isInDimension(entity, EnderDimensions.REALM_KEY);
	}
	public static ServerLevel getDimension() {
		return EndersJourney.getServer().getLevel(EnderDimensions.REALM_KEY);
	}

	public RealmStructure getStructure() {
		if (this.structure == null) {
			EndersJourney.LOGGER.warn("Missing realm structure! Creating..");
			this.structure = new RealmStructure();
		}

		return structure;
	}

	public PlayerManager getPlayer() {
		if (this.player == null) {
			EndersJourney.LOGGER.warn("Missing player manager! Creating..");
			this.player = new PlayerManager(this);
		}

		return this.player;
	}

	public void teleport(LivingEntity entity) {
		Vector3d vec = EndersJourney.getCentre(this.getStructure().getCentre());
		EnderDimensions.teleport(entity, getDimension(), vec, entity.getYRot(), entity.getXRot());
	}

	public static class RealmStructure implements Savable {
		private final ResourceLocation structure;
		private boolean isPlaced;
		private BlockPos centre;
		public RealmStructure(ResourceLocation structure, @Nullable BlockPos centre) {
			this.structure = structure;
			this.isPlaced = false;
			this.centre = centre;
		}
		public RealmStructure() {
			this(getDefaultStructure(), new BlockPos(-5, 162, 1)); // default island structure
		}
		public RealmStructure(CompoundTag data) {
			this.structure = new ResourceLocation(data.getString("Structure"));

			this.deserialise(data);
		}

		@Override
		public CompoundTag serialise() {
			CompoundTag data = new CompoundTag();

			data.putString("Structure", this.structure.toString());

			data.putBoolean("isPlaced", this.isPlaced);
			if (this.centre != null)
				data.put("Centre", NbtUtils.writeBlockPos(this.centre));

			return data;
		}

		@Override
		public void deserialise(CompoundTag data) {
			this.isPlaced = data.getBoolean("isPlaced");

			if (data.contains("Centre")) {
				this.centre = NbtUtils.readBlockPos(data.getCompound("Centre"));
			}
		}

		public boolean isPlaced() {
			return this.isPlaced;
		}

		private static ResourceLocation getDefaultStructure() {
			return new ResourceLocation(EndersJourney.MODID, "island");
		}
		private Optional<StructureTemplate> findStructure() {
			return EndersJourney.getServer().getStructureManager().get(this.structure);
		}

		/**
		 * places the structure if it is not already placed
		 */
		public void verify() {
			if (!this.isPlaced()) {
				this.place();
			}
		}

		private void place() {
			this.place(getDimension(), true);
		}
		private void place(ServerLevel level, boolean inform) {
			if (inform) {
				for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
					p.sendSystemMessage(Component.literal("Please wait while the structure is placed..."));
				}
			}

			long start = System.currentTimeMillis();

			if (this.isPlaced()) {
				EndersJourney.LOGGER.warn("Tried to place realm structure twice");
			}

			StructureTemplate template = this.findStructure().orElse(null);

			if (template == null) {
				EndersJourney.LOGGER.error("Could not find realm structure template");
				return;
			}

			Vec3i size = template.getSize();
			BlockPos offset = new BlockPos(-size.getX() / 2, 0, -size.getZ() / 2);

			template.placeInWorld(
					level,
					offset,
					offset,
					new StructurePlaceSettings(),
					level.getRandom(),
					Block.UPDATE_NONE
			);

			this.isPlaced = true;

			EndersJourney.LOGGER.info("Placed " + this + " at " + offset + " in " + (System.currentTimeMillis() - start) + "ms");
		}

		public BlockPos getCentre() {
			this.verify();

			if (this.centre != null) return this.centre;

			StructureTemplate found = this.findStructure().orElse(null);

			if (found == null) {
				EndersJourney.LOGGER.error("Could not find realm structure template");
				return null;
			}

			Vec3i size = found.getSize();
			this.centre = new BlockPos(0, size.getY() / 4, 0);

			return this.centre;
		}

		@Override
		public String toString() {
			return "RealmStructure{" +
					"structure=" + structure +
					", isPlaced=" + isPlaced +
					", centre=" + centre +
					'}';
		}
	}

	public static class PlayerManager implements Savable {
		private HashMap<UUID, RealmPlayer> seen; // All players this manager has seen before
		private final RealmManager parent;

		public PlayerManager(RealmManager parent) {
			this.parent = parent;
			this.seen = new HashMap<>();
		}

		public PlayerManager(RealmManager parent, CompoundTag data) {
			this(parent);

			this.deserialise(data);
		}

		public boolean hasSeen(RealmPlayer player) {
			return this.hasSeen(player.id);
		}
		public boolean hasSeen(Player player) {
			return this.hasSeen(player.getUUID());
		}
		public boolean hasSeen(UUID id) {
			return this.seen.containsKey(id);
		}

		public void onJoin(Player player) {
			if (player instanceof ServerPlayer) {
				this.runSpawnLogic((ServerPlayer) player);
			}
		}
		public void onLeave(Player player) {

		}
		public void onRespawn(Player player) {
			if (player instanceof ServerPlayer) {
				this.runSpawnLogic((ServerPlayer) player);
			}
		}

		private void runSpawnLogic(ServerPlayer player) {
			if (this.hasSeen(player)) {
				// Assume we dont need to adjust their spawnpoint + teleport.
				return;
			}
			if (this.parent.getStructure().getCentre() == null) return;

			this.parent.teleport(player);
			player.setRespawnPosition(RealmManager.getDimension().dimension(), this.parent.getStructure().getCentre(), 0, true, false);

			this.addPlayer(player);
		}
		private void addPlayer(ServerPlayer player) {
			this.seen.put(player.getUUID(), new RealmPlayer(player));
		}

		private void verifySeen(ServerPlayer player) {
			if (!this.hasSeen(player)) this.runSpawnLogic(player);
		}
		private void verifyAll() {
			long start = System.currentTimeMillis();

			EndersJourney.getServer().getPlayerList().getPlayers().forEach(this::verifySeen);

			EndersJourney.LOGGER.info("Verified all players in " + (System.currentTimeMillis() - start) + "ms");
		}

		@Override
		public CompoundTag serialise() {
			CompoundTag data = new CompoundTag();

			CompoundTag seenData = new CompoundTag();
			this.seen.forEach((uuid, realmPlayer) -> seenData.put(uuid.toString(), realmPlayer.serialise()));
			data.put("Seen", seenData);

			return data;
		}

		@Override
		public void deserialise(CompoundTag data) {
			this.seen = new HashMap<>();
			CompoundTag seenData = data.getCompound("Seen");
			seenData.getAllKeys().forEach(key -> this.seen.put(UUID.fromString(key), new RealmPlayer(seenData.getCompound(key))));
		}
	}

	public static class RealmPlayer implements Savable { // unnecessary now
		private final UUID id;
		private ServerPlayer playerCache;

		public RealmPlayer(UUID id) {
			this.id = id;
		}
		public RealmPlayer(ServerPlayer player) {
			this(player.getUUID());

			this.playerCache = player;
		}
		public RealmPlayer(CompoundTag data) {
			this(data.getUUID("ID"));
		}

		public ServerPlayer asPlayer() {
			if (this.playerCache != null) return this.playerCache;

			return EndersJourney.getServer().getPlayerList().getPlayer(this.id);
		}

		@Override
		public CompoundTag serialise() {
			CompoundTag data = new CompoundTag();

			data.putUUID("ID", this.id);

			return data;
		}

		@Override
		public void deserialise(CompoundTag data) {
		}
	}
}
