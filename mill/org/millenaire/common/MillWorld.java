package org.millenaire.common;

import io.netty.buffer.ByteBufInputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import org.millenaire.common.TileEntityPanel.PanelPacketInfo;
import org.millenaire.common.building.Building;
import org.millenaire.common.core.DevModUtilities;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.core.MillCommonUtilities.ExtFileFilter;
import org.millenaire.common.core.MillCommonUtilities.VillageInfo;
import org.millenaire.common.core.MillCommonUtilities.VillageList;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.network.ServerReceiver;
import org.millenaire.common.network.ServerSender;
import org.millenaire.common.network.StreamReadWrite;

/**
 * Class containing the data on a world that's specific to Millénaire (but not to a particular village).
 * Basically things that would be in the Minecraft world object itself if it could be extended.
 * 
 * For instance, the buildings and villagers arrays contain all buildings and all villagers known to Millénaire in the current world.
 * 
 * @author cedricdj
 */
public class MillWorld {

	public static final String CULTURE_CONTROL = "culturecontrol_";
	public static final String CROP_PLANTING = "cropplanting_";
	public static final String PUJAS = "pujas";
	public static final String MAYANSACRIFICES = "mayansacrifices";

	private final HashMap<Point, Building> buildings = new HashMap<Point, Building>();
	private final HashMap<Point, String> renameNames = new HashMap<Point, String>();
	private final HashMap<Point, String> renameQualifiers = new HashMap<Point, String>();
	public final HashMap<Long, MillVillager> villagers = new HashMap<Long, MillVillager>();
	public final List<String> globalTags = new ArrayList<String>();
	public VillageList loneBuildingsList = new VillageList();
	public File millenaireDir;
	public File saveDir = null;
	public VillageList villagesList = new VillageList();
	public long lastWorldUpdate = 0;

	public HashMap<String, UserProfile> profiles = new HashMap<String, UserProfile>();

	public List<PanelPacketInfo> panelPacketInfos = new ArrayList<PanelPacketInfo>();

	public World world;

	public boolean millenaireEnabled = false;
	private int lastupdate;

	// public List<NBTTagCompound> buildingsToLoad=new
	// List<NBTTagCompound>();

	private static HashMap<Point, String> buildingsTags = new HashMap<Point, String>();
	private static HashMap<Point, Integer> buildingsVariation = new HashMap<Point, Integer>();
	private static HashMap<Point, String> buildingsLocation = new HashMap<Point, String>();

	public MillWorld(final World worldObj) {
		world = worldObj;

		if (!world.isRemote) {
			saveDir = MillCommonUtilities.getWorldSaveDir(world);
			millenaireEnabled = true;
		}

		if (!world.isRemote) {
			millenaireDir = new File(saveDir, "millenaire");

			if (!millenaireDir.exists()) {
				millenaireDir.mkdir();
			}
		}

		Culture.removeServerContent();
	}

	public void addBuilding(final Building b, final Point p) {
		buildings.put(p, b);
	}

	public Collection<Building> allBuildings() {
		return buildings.values();
	}

	public boolean buildingExists(final Point p) {
		return buildings.containsKey(p);
	}

	public void checkConnections() {

		for (final UserProfile profile : profiles.values()) {

			if (profile.connected) {
				if (profile.getPlayer() == null) {
					profile.disconnectUser();
				}
			}
		}

	}

	public void clearGlobalTag(final String tag) {
		if (globalTags.contains(tag)) {
			globalTags.remove(tag);
			saveGlobalTags();

			if (!world.isRemote) {
				for (final UserProfile up : profiles.values()) {
					if (up.connected) {
						up.sendProfilePacket(UserProfile.UPDATE_GLOBAL_TAGS);
					}
				}
			}
		}
	}

	public void clearPanelQueue() {
		final List<PanelPacketInfo> toDelete = new ArrayList<PanelPacketInfo>();

		for (final PanelPacketInfo pinfo : panelPacketInfos) {
			final TileEntityPanel panel = pinfo.pos.getPanel(world);

			if (panel != null) {
				panel.panelType = pinfo.panelType;
				panel.buildingPos = pinfo.buildingPos;
				panel.villager_id = pinfo.villager_id;

				for (int i = 0; i < pinfo.lines.length && i < panel.signText.length; i++) {
					panel.signText[i] = MLN.string(pinfo.lines[i]);
				}

				toDelete.add(pinfo);
			}
		}

		for (final PanelPacketInfo pinfo : toDelete) {
			panelPacketInfos.remove(pinfo);
		}
	}

	public void displayTagActionData(final EntityPlayer player) {
		String s = "";
		for (final String tag : globalTags) {
			s += tag + " ";
		}
		ServerSender.sendChat(player, EnumChatFormatting.GREEN, "Tags: " + s);

		ServerSender.sendChat(player, EnumChatFormatting.GREEN, "ActionData: " + s);
		final String biomeName = world.getWorldChunkManager().getBiomeGenAt((int) player.posX, (int) player.posZ).biomeName.toLowerCase();
		ServerSender.sendChat(player, EnumChatFormatting.GREEN, "Biome: " + biomeName + ", time: " + world.getWorldTime() % 24000 + " / " + world.getWorldTime());
	}

	public void displayVillageList(final EntityPlayer player, final boolean loneBuildings) {

		VillageList list;

		if (loneBuildings) {
			list = loneBuildingsList;
		} else {
			list = villagesList;
		}

		final List<VillageInfo> villageList = new ArrayList<VillageInfo>();

		for (int i = 0; i < list.names.size(); i++) {

			final Point p = list.pos.get(i);

			final int distance = MathHelper.floor_double(p.horizontalDistanceTo(player));

			if (distance <= MLN.BackgroundRadius) {
				final String direction = new Point(player).directionTo(p, true);

				String loaded;

				final Building townHall = getBuilding(p);

				if (townHall == null) {
					loaded = "command.inactive";
				} else if (townHall.isActive) {
					loaded = "command.active";
				} else {
					if (!townHall.isAreaLoaded) {
						loaded = "command.inactive";
					} else {
						loaded = "command.frozen";
					}
				}

				VillageType villageType;

				if (loneBuildings) {
					villageType = Culture.getCultureByName(list.cultures.get(i)).getLoneBuildingType(list.types.get(i));
				} else {
					villageType = Culture.getCultureByName(list.cultures.get(i)).getVillageType(list.types.get(i));
				}

				final VillageInfo vi = new VillageInfo();
				vi.distance = distance;

				if (villageType != null) {
					vi.textKey = "command.villagelist";
					vi.values = new String[] { list.names.get(i), loaded, "" + distance, direction, villageType.name };
				}
				villageList.add(vi);
			}
		}

		if (!loneBuildings) {// we need to add "quest" lone buildings
			for (int i = 0; i < loneBuildingsList.names.size(); i++) {

				final VillageType village = Culture.getCultureByName(loneBuildingsList.cultures.get(i)).getLoneBuildingType(loneBuildingsList.types.get(i));

				if (village.keyLonebuilding || village.keyLoneBuildingGenerateTag != null) {

					if (!village.generatedForPlayer || player.getName().equalsIgnoreCase(loneBuildingsList.generatedFor.get(i))) {

						final Point p = loneBuildingsList.pos.get(i);

						final int distance = MathHelper.floor_double(p.horizontalDistanceTo(player));

						if (distance <= 2000) {
							final String direction = new Point(player).directionTo(p, true);

							final VillageInfo vi = new VillageInfo();
							vi.distance = distance;

							if (village != null) {
								vi.textKey = "command.villagelistkeylonebuilding";
								vi.values = new String[] { village.name, "" + distance, direction };
							}
							villageList.add(vi);
						}
					}
				}
			}
		}

		if (villageList.size() == 0) {
			ServerSender.sendTranslatedSentence(player, MLN.LIGHTGREY, "command.noknowvillage");
		} else {

			Collections.sort(villageList);

			for (final VillageInfo vi : villageList) {
				ServerSender.sendTranslatedSentence(player, MLN.LIGHTGREY, vi.textKey, vi.values);
			}
		}
	}

	public void forcePreload() {

		if (world.isRemote || MLN.forcePreload <= 0) {
			return;
		}

		lastupdate++;

		if (lastupdate < 50) {
			return;
		}

		lastupdate = 0;

		int centreX, centreZ;

		if (world.playerEntities.size() > 0) {

			final Object o = world.playerEntities.get(0);
			final EntityPlayer player = (EntityPlayer) o;

			centreX = (int) (player.posX / 16);
			centreZ = (int) (player.posZ / 16);
		} else {
			centreX = world.getSpawnPoint().posX / 16;
			centreZ = world.getSpawnPoint().posZ / 16;
		}

		int nbGenerated = 0;

		for (int radius = 1; radius < MLN.forcePreload; radius++) {
			for (int i = -MLN.forcePreload; i < MLN.forcePreload && nbGenerated < 100; i++) {
				for (int j = -MLN.forcePreload; j < MLN.forcePreload && nbGenerated < 100; j++) {
					if (i * i + j * j < radius * radius) {
						if (!world.getChunkProvider().chunkExists(i + centreX, j + centreZ)) {
							world.getChunkProvider().loadChunk(i + centreX, j + centreZ);
							final Block block = MillCommonUtilities.getBlock(world, (i + centreX) * 16, 60, (j + centreZ) * 16);
							world.getChunkProvider().saveChunks(false, null);
							MLN.minor(this, "Forcing population of chunk " + (i + centreX) + "/" + (j + centreZ) + ", block: " + block);
							nbGenerated++;
						}
					}
				}
			}
		}

	}

	public Building getBuilding(final Point p) {
		if (buildings.containsKey(p)) {

			if (buildings.get(p) == null) {
				MLN.error(this, "Building record for " + p + " is null.");
			} else if (buildings.get(p).location == null) {
				MLN.printException("Building location for " + p + " is null.", new Exception());
			}

			return buildings.get(p);
		}

		if (MLN.LogWorldInfo >= MLN.MINOR) {
			MLN.minor(this, "Could not find a building at location " + p + " amoung " + buildings.size() + " records.");
		}

		return null;
	}

	public Building getClosestVillage(final Point p) {

		int bestDistance = Integer.MAX_VALUE;
		Building bestVillage = null;

		for (final Point villageCoord : villagesList.pos) {
			final int dist = (int) p.distanceToSquared(villageCoord);
			if (bestVillage == null || dist < bestDistance) {
				final Building village = getBuilding(villageCoord);
				if (village != null) {
					bestVillage = village;
					bestDistance = dist;
				}
			}
		}

		return bestVillage;
	}

	public List<Point> getCombinedVillagesLoneBuildings() {
		final List<Point> thPosLists = new ArrayList<Point>(villagesList.pos);
		thPosLists.addAll(loneBuildingsList.pos);
		return thPosLists;
	}

	public UserProfile getProfile(final String name) {
		if (profiles.containsKey(name)) {
			return profiles.get(name);
		}

		// 3.0 "single player" profile
		if (profiles.containsKey(UserProfile.OLD_PROFILE_SINGLE_PLAYER)) {

			final UserProfile profile = profiles.get(UserProfile.OLD_PROFILE_SINGLE_PLAYER);
			profile.changeProfileKey(name);

			return profile;
		}

		final UserProfile profile = new UserProfile(this, name, name);
		profiles.put(profile.key, profile);
		return profile;
	}

	public boolean isGlobalTagSet(final String tag) {
		return globalTags.contains(tag);
	}

	// Server-side only
	private void loadBuildings() {

		final long startTime = System.currentTimeMillis();

		final File buildingsDir = new File(millenaireDir, "buildings");

		if (!buildingsDir.exists()) {
			buildingsDir.mkdir();
		}

		for (final File file : buildingsDir.listFiles(new ExtFileFilter("gz"))) {
			try {
				final FileInputStream fileinputstream = new FileInputStream(file);
				final NBTTagCompound nbttagcompound = CompressedStreamTools.readCompressed(fileinputstream);

				final NBTTagList nbttaglist = nbttagcompound.getTagList("buildings", Constants.NBT.TAG_COMPOUND);
				for (int i = 0; i < nbttaglist.tagCount(); i++) {
					final NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
					new Building(this, nbttagcompound1);
				}

			} catch (final Exception e) {
				MLN.printException("Error when attempting to load building file " + file.getAbsolutePath() + ":", e);
			}
		}

		if (MLN.LogHybernation >= MLN.MAJOR) {
			for (final Building b : buildings.values()) {
				MLN.major(null, b + " - " + b.culture);
			}

			MLN.major(this, "Loaded " + buildings.size() + " in " + (System.currentTimeMillis() - startTime) + " ms.");
		}

	}

	public void loadData() {

		if (world.isRemote) {
			return;
		}

		loadWorldConfig();
		loadVillageList();
		loadGlobalTags();
		loadBuildings();
		loadProfiles();

		Mill.startMessageDisplayed = false;
	}

	private void loadGlobalTags() {
		final File tagsFile = new File(millenaireDir, "tags.txt");

		globalTags.clear();

		if (tagsFile.exists()) {
			try {

				final BufferedReader reader = MillCommonUtilities.getReader(tagsFile);
				String line = reader.readLine();

				while (line != null) {

					if (line.trim().length() > 0) {
						globalTags.add(line.trim());
					}
					line = reader.readLine();
				}

				if (MLN.LogWorldGeneration >= MLN.MAJOR) {
					MLN.major(null, "Loaded " + globalTags.size() + " tags.");
				}

			} catch (final Exception e) {
				MLN.printException(e);
			}
		}

	}

	private void loadProfiles() {
		final File profilesDir = new File(millenaireDir, "profiles");

		if (!profilesDir.exists()) {
			profilesDir.mkdirs();
		}

		for (final File profileDir : profilesDir.listFiles()) {
			if (profileDir.isDirectory() && !profileDir.isHidden()) {
				final UserProfile profile = UserProfile.readProfile(this, profileDir);

				if (profile != null) {
					profiles.put(profile.key, profile);
				}
			}
		}
	}

	private void loadVillageList() {
		File villageLog = new File(millenaireDir, "villages.txt");

		if (villageLog.exists()) {
			try {

				final BufferedReader reader = MillCommonUtilities.getReader(villageLog);
				String line = reader.readLine();

				while (line != null) {

					if (line.trim().length() > 0) {

						final String[] p = line.split(";")[1].split("/");

						String type = "";
						if (line.split(";").length > 2) {
							type = line.split(";")[2];
						}

						String culture = "";
						if (line.split(";").length > 3) {
							culture = line.split(";")[3];
						}

						final Culture c = Culture.getCultureByName(culture);

						String generatedFor = null;
						if (line.split(";").length > 4) {
							generatedFor = line.split(";")[4];
						}

						registerVillageLocation(world, new Point(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), line.split(";")[0], c.getVillageType(type), c, false,
								generatedFor);
					}

					line = reader.readLine();
				}

				if (MLN.LogWorldGeneration >= MLN.MAJOR) {
					MLN.major(null, "Loaded " + villagesList.names.size() + " village positions.");
				}

			} catch (final Exception e) {
				MLN.printException(e);
			}
		}

		villageLog = new File(millenaireDir, "lonebuildings.txt");

		if (villageLog.exists()) {
			try {

				final BufferedReader reader = MillCommonUtilities.getReader(villageLog);
				String line = reader.readLine();

				while (line != null) {

					if (line.trim().length() > 0) {

						final String[] p = line.split(";")[1].split("/");

						String type = "";
						if (line.split(";").length > 2) {
							type = line.split(";")[2];
						}

						String culture = "";
						if (line.split(";").length > 3) {
							culture = line.split(";")[3];
						}

						final Culture c = Culture.getCultureByName(culture);

						String generatedFor = null;
						if (line.split(";").length > 4) {
							generatedFor = line.split(";")[4];
						}

						registerLoneBuildingsLocation(world, new Point(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), line.split(";")[0], c.getLoneBuildingType(type), c,
								false, generatedFor);
					}

					line = reader.readLine();
				}

				if (MLN.LogWorldGeneration >= MLN.MAJOR) {
					MLN.major(null, "Loaded " + loneBuildingsList.names.size() + " lone buildings positions.");
				}

			} catch (final Exception e) {
				MLN.printException(e);
			}
		}
	}

	private void loadWorldConfig() {

		MLN.generateVillages = MLN.generateVillagesDefault;

		renameNames.clear();
		renameQualifiers.clear();

		final File configFile = new File(millenaireDir, "config.txt");

		if (configFile != null && configFile.exists()) {
			try {

				final BufferedReader reader = MillCommonUtilities.getReader(configFile);

				String line;

				while ((line = reader.readLine()) != null) {
					if (line.trim().length() > 0 && !line.startsWith("//")) {
						final String[] temp = line.split("=");
						if (temp.length == 2) {

							final String key = temp[0];
							final String value = temp[1];

							if (key.equalsIgnoreCase("generate_villages")) {
								MLN.generateVillages = Boolean.parseBoolean(value);
							} else if (key.equalsIgnoreCase("rename_name")) {
								final Point p = new Point(value.split(",")[0]);
								renameNames.put(p, value.split(",")[1]);
							} else if (key.equalsIgnoreCase("rename_qualifier")) {
								final Point p = new Point(value.split(",")[0]);
								if (value.split(",").length > 1) {
									renameQualifiers.put(p, value.split(",")[1]);
								} else {
									renameQualifiers.put(p, "");
								}
							}
						}
					}
				}
				reader.close();

			} catch (final IOException e) {
				MLN.printException(e);
			}
		}

		if (MLN.LogWorldGeneration >= MLN.MAJOR) {
			MLN.major(null, "Config loaded. generateVillages: " + MLN.generateVillages);
		}
	}

	public int nbCultureInGeneratedVillages() {

		final List<String> cultures = new ArrayList<String>();

		for (int i = 0; i < villagesList.names.size(); i++) {
			if (!cultures.contains(villagesList.cultures.get(i))) {
				cultures.add(villagesList.cultures.get(i));
			}
		}

		return cultures.size();
	}

	public void receiveVillageListPacket(final ByteBufInputStream ds) {

		if (MLN.LogNetwork >= MLN.MINOR) {
			MLN.minor(this, "Received village list packet.");
		}

		villagesList = new VillageList();
		loneBuildingsList = new VillageList();

		try {
			int nb = ds.readInt();
			for (int i = 0; i < nb; i++) {
				villagesList.pos.add(StreamReadWrite.readNullablePoint(ds));
				villagesList.names.add(StreamReadWrite.readNullableString(ds));
				villagesList.cultures.add(StreamReadWrite.readNullableString(ds));
				villagesList.types.add(StreamReadWrite.readNullableString(ds));
			}

			nb = ds.readInt();
			for (int i = 0; i < nb; i++) {
				loneBuildingsList.pos.add(StreamReadWrite.readNullablePoint(ds));
				loneBuildingsList.names.add(StreamReadWrite.readNullableString(ds));
				loneBuildingsList.cultures.add(StreamReadWrite.readNullableString(ds));
				loneBuildingsList.types.add(StreamReadWrite.readNullableString(ds));
			}

		} catch (final IOException e) {
			MLN.printException(this + ": Error in receiveVillageListPacket", e);
		}
	}

	public void registerLoneBuildingsLocation(final World world, final Point pos, final String name, final VillageType type, final Culture culture, final boolean newVillage, String playerName) {

		boolean found = false;

		for (final Point p : loneBuildingsList.pos) {
			if (p.equals(pos)) {
				found = true;
			}
		}

		if (found) {
			return;
		}

		if (!type.generatedForPlayer) {
			playerName = null;
		}

		loneBuildingsList.addVillage(pos, name, type.key, culture.key, playerName);

		if (MLN.LogWorldGeneration >= MLN.MAJOR) {
			MLN.major(null, "Registering lone buildings: " + name + " / " + type + " / " + culture + " / " + pos);
		}
		for (final Object o : world.playerEntities) {
			final EntityPlayer player = (EntityPlayer) o;
			if (newVillage && (type.keyLonebuilding || type.keyLoneBuildingGenerateTag != null)) {
				final int distance = MathHelper.floor_double(pos.horizontalDistanceTo(player));

				if (distance <= 2000) {
					final String direction = new Point(player).directionTo(pos, true);
					ServerSender.sendTranslatedSentence(player, MLN.YELLOW, "command.newlonebuildingfound", type.name, "" + distance, direction);
				}
			}
			// sendVillageListPacket(player);
		}

		saveLoneBuildingsList();

	}

	public void registerVillageLocation(final World world, final Point pos, final String name, final VillageType type, final Culture culture, final boolean newVillage, String playerName) {

		boolean found = false;

		if (type == null) {
			MLN.error(null, "Attempting to register village with null type: " + pos + "/" + culture + "/" + name + "/" + newVillage);
			return;
		}

		if (culture == null) {
			MLN.error(null, "Attempting to register village with null culture: " + pos + "/" + type + "/" + name + "/" + newVillage);
			return;
		}

		for (final Point p : villagesList.pos) {
			if (p.equals(pos)) {
				found = true;
			}
		}

		if (found) {
			return;
		}

		if (!type.generatedForPlayer) {
			playerName = null;
		}

		villagesList.addVillage(pos, name, type.key, culture.key, playerName);

		if (MLN.LogWorldGeneration >= MLN.MAJOR) {
			MLN.major(null, "Registering village: " + name + " / " + type + " / " + culture + " / " + pos);
		}

		if (newVillage) {
			for (final Object o : world.playerEntities) {
				final EntityPlayer player = (EntityPlayer) o;

				final int distance = MathHelper.floor_double(pos.horizontalDistanceTo(player));

				if (distance <= 2000 && !world.isRemote) {
					final String direction = new Point(player).directionTo(pos, true);
					ServerSender.sendTranslatedSentence(player, MLN.YELLOW, "command.newvillagefound", name, type.name, "culture." + culture.key, "" + distance, direction);
				}

				// sendVillageListPacket(player);
			}
		}

		saveVillageList();
	}

	public void removeBuilding(final Point p) {
		buildings.remove(p);
	}

	public void removeVillageOrLoneBuilding(final Point p) {

		loneBuildingsList.removeVillage(p);
		villagesList.removeVillage(p);
		saveLoneBuildingsList();
		saveVillageList();

		// for (final Object o : world.playerEntities) {
		// final EntityPlayer player=(EntityPlayer)o;
		// sendVillageListPacket(player);
		// }
	}

	public void saveEverything() {

		if (world.isRemote) {
			return;
		}

		saveGlobalTags();
		saveLoneBuildingsList();
		saveVillageList();
		saveWorldConfig();

		for (final Building b : buildings.values()) {

			if (b.isTownhall && b.isActive) {
				b.saveTownHall("world save");
			}

		}

	}

	private void saveGlobalTags() {

		if (world.isRemote) {
			return;
		}

		final File configFile = new File(millenaireDir, "tags.txt");

		try {
			final BufferedWriter writer = MillCommonUtilities.getWriter(configFile);

			for (final String tag : globalTags) {
				writer.write(tag + MLN.EOL);
			}
			writer.flush();

		} catch (final IOException e) {
			MLN.printException(e);
		}
	}

	public void saveLoneBuildingsList() {

		if (world.isRemote) {
			return;
		}

		final File millenaireDir = new File(saveDir, "millenaire");

		if (!millenaireDir.exists()) {
			millenaireDir.mkdir();
		}

		final File villageLog = new File(millenaireDir, "lonebuildings.txt");

		try {

			final BufferedWriter writer = MillCommonUtilities.getWriter(villageLog);

			for (int i = 0; i < loneBuildingsList.pos.size(); i++) {
				final Point p = loneBuildingsList.pos.get(i);

				String generatedFor = loneBuildingsList.generatedFor.get(i);

				if (generatedFor == null) {
					generatedFor = "";
				}

				writer.write(loneBuildingsList.names.get(i) + ";" + p.getiX() + "/" + p.getiY() + "/" + p.getiZ() + ";" + loneBuildingsList.types.get(i) + ";" + loneBuildingsList.cultures.get(i)
						+ ";" + generatedFor + System.getProperty("line.separator"));
			}
			writer.flush();
			if (MLN.LogWorldGeneration >= MLN.MAJOR) {
				MLN.major(null, "Saved " + loneBuildingsList.names.size() + " lone buildings.txt positions.");
			}

		} catch (final IOException e) {
			MLN.printException(e);
		}

	}

	public void saveVillageList() {

		if (world.isRemote) {
			return;
		}

		final File millenaireDir = new File(saveDir, "millenaire");

		if (!millenaireDir.exists()) {
			millenaireDir.mkdir();
		}

		final File villageLog = new File(millenaireDir, "villages.txt");

		try {

			final BufferedWriter writer = MillCommonUtilities.getWriter(villageLog);

			for (int i = 0; i < villagesList.pos.size(); i++) {
				final Point p = villagesList.pos.get(i);

				String generatedFor = villagesList.generatedFor.get(i);

				if (generatedFor == null) {
					generatedFor = "";
				}

				writer.write(villagesList.names.get(i) + ";" + p.getiX() + "/" + p.getiY() + "/" + p.getiZ() + ";" + villagesList.types.get(i) + ";" + villagesList.cultures.get(i) + ";"
						+ generatedFor + System.getProperty("line.separator"));
			}
			writer.flush();
			if (MLN.LogWorldGeneration >= MLN.MAJOR) {
				MLN.major(null, "Saved " + villagesList.names.size() + " village positions.");
			}

		} catch (final IOException e) {
			MLN.printException(e);
		}
	}

	public void saveWorldConfig() {

		if (world.isRemote) {
			return;
		}

		final File configFile = new File(millenaireDir, "config.txt");

		try {

			final BufferedWriter writer = MillCommonUtilities.getWriter(configFile);

			writer.write("generate_villages=" + MLN.generateVillages + MLN.EOL);

			writer.flush();

		} catch (final IOException e) {
			MLN.printException(e);
		}
	}

	public void sendVillageListPacket(final EntityPlayer player) {
		final DataOutput data = ServerSender.getNewByteBufOutputStream();

		try {
			data.write(ServerReceiver.PACKET_VILLAGELIST);

			data.writeInt(villagesList.pos.size());
			for (int i = 0; i < villagesList.pos.size(); i++) {
				StreamReadWrite.writeNullablePoint(villagesList.pos.get(i), data);
				StreamReadWrite.writeNullableString(villagesList.names.get(i), data);
				StreamReadWrite.writeNullableString(villagesList.cultures.get(i), data);
				StreamReadWrite.writeNullableString(villagesList.types.get(i), data);
			}

			data.writeInt(loneBuildingsList.pos.size());
			for (int i = 0; i < loneBuildingsList.pos.size(); i++) {
				StreamReadWrite.writeNullablePoint(loneBuildingsList.pos.get(i), data);
				StreamReadWrite.writeNullableString(loneBuildingsList.names.get(i), data);
				StreamReadWrite.writeNullableString(loneBuildingsList.cultures.get(i), data);
				StreamReadWrite.writeNullableString(loneBuildingsList.types.get(i), data);
			}

		} catch (final IOException e) {
			MLN.printException(this + ": Error in sendVillageListPacket", e);
		}

		ServerSender.sendPacketToPlayer(ServerSender.createServerPacket(data), player);
	}

	public void setGlobalTag(final String tag) {
		if (!globalTags.contains(tag)) {
			globalTags.add(tag);
			saveGlobalTags();

			if (!world.isRemote) {
				for (final UserProfile up : profiles.values()) {
					if (up.connected) {
						up.sendProfilePacket(UserProfile.UPDATE_GLOBAL_TAGS);
					}
				}
			}
		}
	}

	public void testLocations(final String label) {

		if (!MLN.DEV) {
			return;
		}

		for (final Building b : allBuildings()) {

			try {

				if (b.location != null) {

					String tags = "";

					for (final String s : b.location.tags) {
						tags += s + ";";
					}

					if (!buildingsTags.containsKey(b.getPos())) {
						MLN.minor(null, "Detected new building: " + b + " with tags: " + tags);
						buildingsTags.put(b.getPos(), tags);
					} else {
						if (!tags.equals(buildingsTags.get(b.getPos()))) {
							MLN.warning(null, "Testing locations due to: " + label);
							MLN.warning(null, "Tags changed for building: " + b + ". Was: " + buildingsTags.get(b.getPos()) + " now: " + tags);
							buildingsTags.put(b.getPos(), tags);
						}
					}

					if (!buildingsVariation.containsKey(b.getPos())) {
						MLN.minor(null, "Detected new building: " + b + " with variation: " + b.location.getVariation());
						buildingsVariation.put(b.getPos(), b.location.getVariation());
					} else {
						if (!buildingsVariation.get(b.getPos()).equals(b.location.getVariation())) {
							MLN.warning(null, "Testing locations due to: " + label);
							MLN.warning(null, "Variation changed for building: " + b + ". Was: " + buildingsVariation.get(b.getPos()) + " now: " + b.location.getVariation());
							buildingsVariation.put(b.getPos(), b.location.getVariation());
						}
					}

					if (!buildingsLocation.containsKey(b.getPos())) {
						MLN.minor(null, "Detected new building: " + b + " with location key: " + b.location.planKey);
						buildingsLocation.put(b.getPos(), b.location.planKey);
					} else {
						if (!b.location.planKey.equals(buildingsLocation.get(b.getPos()))) {
							MLN.warning(null, "Testing locations due to: " + label);
							MLN.warning(null, "Location key changed for building: " + b + ". Was: " + buildingsLocation.get(b.getPos()) + " now: " + b.location.planKey);
							buildingsLocation.put(b.getPos(), b.location.planKey);
						}
					}
				}
			} catch (final Exception e) {
				MLN.printException("Error in dev monitoring of a building building: ", e);
			}
		}

	}

	private void testLog() {
		if (!MLN.logPerformed) {
			if (Mill.proxy.isTrueServer()) {
				MillCommonUtilities.logInstance(world);
			} else if (!(world instanceof WorldServer)) {
				MillCommonUtilities.logInstance(world);
			}
		}
	}

	@Override
	public String toString() {
		return "World(" + world.getWorldInfo().getSeed() + ")";
	}

	public void updateWorldClient(final boolean surfaceLoaded) {

		if (!Mill.checkedMillenaireDir && (!Mill.proxy.getBaseDir().exists() || !new File(Mill.proxy.getBaseDir(), "config.txt").exists())) {
			Mill.proxy.sendChatAdmin("The millenaire directory could not be found. It should be inside the minecraft directory, alongside \"bin\".");
			Mill.proxy.sendChatAdmin("Le dossier millenaire est introuvable. Il devrait \u00eatre dans le dossier minecraft, \u00e0 c\u00f4t\u00e9 de \"bin\".");

		}
		Mill.checkedMillenaireDir = true;

		if (surfaceLoaded) {
			for (final Building b : allBuildings()) {
				b.updateBuildingClient();
			}
		}

		Mill.proxy.checkTextureSize();

		testLog();

	}

	public void updateWorldServer() {

		for (final Building b : allBuildings()) {
			b.updateBuildingServer();
			b.updateBackgroundVillage();
		}

		checkConnections();

		for (final UserProfile profile : this.profiles.values()) {
			if (!profile.connected && profile.getPlayer() != null) {
				profile.connectUser();
			}
			if (profile.connected) {
				profile.updateProfile();
			}
		}

		for (final Object o : world.playerEntities) {
			final EntityPlayer player = (EntityPlayer) o;

			// MillCommonUtilities.getServerProfile(player.worldObj,player.getDisplayName()).updateProfile();

			SpecialQuestActions.onTick(this, player);
		}

		if (MLN.DEV) {

			// testLocations("worldupdate");

			DevModUtilities.runAutoMove(world);
		}

		for (final Point p : renameNames.keySet()) {
			if (buildings.containsKey(p)) {
				final Building b = buildings.get(p);
				b.changeVillageName(renameNames.get(p));
				for (int i = 0; i < villagesList.pos.size(); i++) {
					if (villagesList.pos.get(i).equals(p)) {
						villagesList.names.add(i, b.getVillageQualifiedName());
					}
				}
				for (int i = 0; i < loneBuildingsList.pos.size(); i++) {
					if (loneBuildingsList.pos.get(i).equals(p)) {
						loneBuildingsList.names.add(i, b.getVillageQualifiedName());
					}
				}
			}
		}
		for (final Point p : renameQualifiers.keySet()) {
			if (buildings.containsKey(p)) {
				final Building b = buildings.get(p);
				b.changeVillageQualifier(renameQualifiers.get(p));
				for (int i = 0; i < villagesList.pos.size(); i++) {
					if (villagesList.pos.get(i).equals(p)) {
						villagesList.names.add(i, b.getVillageQualifiedName());
					}
				}
				for (int i = 0; i < loneBuildingsList.pos.size(); i++) {
					if (loneBuildingsList.pos.get(i).equals(p)) {
						loneBuildingsList.names.add(i, b.getVillageQualifiedName());
					}
				}
			}
		}
		renameNames.clear();
		renameQualifiers.clear();

		forcePreload();

		testLog();

	}

}
