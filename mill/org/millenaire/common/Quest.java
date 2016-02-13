package org.millenaire.common;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import org.millenaire.common.MLN.MillenaireException;
import org.millenaire.common.building.Building;
import org.millenaire.common.core.MillCommonUtilities;
import org.millenaire.common.core.MillCommonUtilities.ExtFileFilter;
import org.millenaire.common.forge.Mill;
import org.millenaire.common.forge.MillAchievements;
import org.millenaire.common.item.Goods;
import org.millenaire.common.item.Goods.IItemInitialEnchantmens;
import org.millenaire.common.network.ServerSender;

public class Quest {

	public static class QuestInstance {

		private static final int QUEST_LANGUAGE_BONUS = 50;

		public static QuestInstance loadFromString(final MillWorld mw, final String line, final UserProfile profile) {
			Quest q = null;
			int step = 0;
			long startTime = 0, stepStartTime = 0;

			final HashMap<String, QuestInstanceVillager> villagers = new HashMap<String, QuestInstanceVillager>();

			for (final String s : line.split(";")) {
				if (s.split(":").length == 2) {
					final String key = s.split(":")[0];
					final String value = s.split(":")[1];

					if (key.equals("quest")) {
						if (quests.containsKey(value)) {
							q = quests.get(value);
						} else {
							MLN.error(null, "Could not find quest '" + value + "'.");
						}
					} else if (key.equals("startTime")) {
						startTime = Long.parseLong(value);
					} else if (key.equals("currentStepStartTime")) {
						stepStartTime = Long.parseLong(value);
					} else if (key.equals("step")) {
						step = Integer.parseInt(value);
					} else if (key.equals("villager")) {
						final String[] vals = value.split(",");
						final QuestInstanceVillager qiv = new QuestInstanceVillager(mw, new Point(vals[2]), Long.parseLong(vals[1]));
						villagers.put(vals[0], qiv);
					}
				}
			}

			if (q != null && villagers.size() > 0) {
				return new QuestInstance(mw, q, profile, villagers, startTime, step, stepStartTime);
			}
			return null;
		}

		public int currentStep = 0;
		public long currentStepStart;
		public Quest quest;
		public long startTime;
		public HashMap<String, QuestInstanceVillager> villagers;
		public UserProfile profile = null;
		public MillWorld mw;
		public World world;
		// for networked exchange only
		public long uniqueid;

		public QuestInstance(final MillWorld mw, final Quest quest, final UserProfile profile, final HashMap<String, QuestInstanceVillager> villagers, final long startTime) {
			this(mw, quest, profile, villagers, startTime, 0, startTime);
		}

		public QuestInstance(final MillWorld mw, final Quest quest, final UserProfile profile, final HashMap<String, QuestInstanceVillager> villagers, final long startTime, final int step,
				final long stepStartTime) {
			this.mw = mw;
			world = mw.world;
			this.villagers = villagers;
			this.quest = quest;
			this.currentStep = step;
			this.startTime = startTime;
			this.profile = profile;
			currentStepStart = stepStartTime;
			uniqueid = (long) (Math.random() * Long.MAX_VALUE);
		}

		private void applyActionData(final List<String[]> data) {
			for (final String[] val : data) {
				profile.setActionData(val[0], val[1]);
			}
		}

		private void applyGlobalTags(final List<String> set, final List<String> clear) {
			if (MLN.LogQuest >= MLN.DEBUG) {
				MLN.debug(this, "Applying " + set.size() + " global tags, clearing " + clear.size() + " global tags.");
			}
			for (final String val : set) {
				mw.setGlobalTag(val);
			}
			for (final String val : clear) {
				mw.clearGlobalTag(val);
			}
		}

		private void applyPlayerTags(final List<String> set, final List<String> clear) {
			if (MLN.LogQuest >= MLN.DEBUG) {
				MLN.debug(this, "Applying " + set.size() + " player tags, clearing " + clear.size() + " player tags.");
			}
			for (final String val : set) {
				profile.setTag(val);
			}
			for (final String val : clear) {
				profile.clearTag(val);
			}
		}

		private void applyTags(final List<String[]> set, final List<String[]> clear) {
			if (MLN.LogQuest >= MLN.DEBUG) {
				MLN.debug(this, "Applying " + set.size() + " tags, clearing " + clear.size() + " tags.");
			}
			for (final String[] val : set) {

				final String tag = profile.key + "_" + val[1];

				if (MLN.LogQuest >= MLN.DEBUG) {
					MLN.debug(this, "Applying tag: " + val[0] + "/" + tag);
				}
				if (!villagers.get(val[0]).getVillagerRecord(world).questTags.contains(tag)) {
					villagers.get(val[0]).getVillagerRecord(world).questTags.add(tag);
					villagers.get(val[0]).getVillagerRecord(world).getTownHall().requestSave("quest tag");
					if (MLN.LogQuest >= MLN.MINOR) {
						MLN.minor(this, "Setting tag: " + tag + " on villager: " + val[0] + " (" + villagers.get(val[0]).getVillagerRecord(world).getName() + ") Now present: "
								+ villagers.get(val[0]).getVillagerRecord(world).questTags.size());
					}

				}
			}
			for (final String[] val : clear) {
				final String tag = profile.key + "_" + val[1];

				if (MLN.LogQuest >= MLN.DEBUG) {
					MLN.debug(this, "Clearing tag: " + val[0] + "/" + tag);
				}
				villagers.get(val[0]).getVillagerRecord(world).questTags.remove(tag);
				villagers.get(val[0]).getVillagerRecord(world).getTownHall().requestSave("quest tag");
				if (MLN.LogQuest >= MLN.MINOR) {
					MLN.minor(this, "Clearing tag: " + tag + " on villager: " + val[0] + " (" + villagers.get(val[0]).getVillagerRecord(world).getName() + ")");
				}
			}
		}

		public boolean checkStatus(final World world) {

			if (currentStepStart + getCurrentStep().duration * 1000 <= world.getWorldTime()) {
				final MillVillager cv = getCurrentVillager().getVillager(world);
				if (cv != null && getCurrentStep().penaltyReputation > 0) {
					profile.adjustReputation(cv.getTownHall(), -getCurrentStep().penaltyReputation);
				}

				applyTags(getCurrentStep().setVillagerTagsFailure, getCurrentStep().clearTagsFailure);
				applyGlobalTags(getCurrentStep().setGlobalTagsFailure, getCurrentStep().clearGlobalTagsFailure);
				applyPlayerTags(getCurrentStep().setPlayerTagsFailure, getCurrentStep().clearPlayerTagsFailure);

				if (getCurrentStep().getDescriptionTimeUp() != null) {
					ServerSender.sendChat(profile.getPlayer(), EnumChatFormatting.RED, getDescriptionTimeUp(profile) + " (" + MLN.string("quest.reputationlost") + ": "
							+ getCurrentStep().penaltyReputation + ")");
				}

				destroySelf();

				return true;
			}
			return false;
		}

		public String completeStep(final EntityPlayer player, final MillVillager villager) {

			String reward = "";

			for (final InvItem item : getCurrentStep().requiredGood.keySet()) {
				if (item.special == 0) {
					villager.addToInv(item.getItem(), item.meta, getCurrentStep().requiredGood.get(item));
					MillCommonUtilities.getItemsFromChest(player.inventory, item.getItem(), item.meta, getCurrentStep().requiredGood.get(item));
				}
			}

			for (final InvItem item : getCurrentStep().rewardGoods.keySet()) {
				final int nbLeft = getCurrentStep().rewardGoods.get(item) - MillCommonUtilities.putItemsInChest(player.inventory, item.getItem(), item.meta, getCurrentStep().rewardGoods.get(item));

				if (nbLeft > 0) {// couldn't fit in inventory
					final EntityItem entItem = MillCommonUtilities.spawnItem(world, villager.getPos(), new ItemStack(item.getItem(), nbLeft, item.meta), 0);

					if (entItem.getEntityItem().getItem() instanceof IItemInitialEnchantmens) {
						((IItemInitialEnchantmens) entItem.getEntityItem().getItem()).applyEnchantments(entItem.getEntityItem());
					}
				}

				reward += " " + getCurrentStep().rewardGoods.get(item) + " " + item.getName();
			}

			if (getCurrentStep().rewardMoney > 0) {
				MillCommonUtilities.changeMoney(player.inventory, getCurrentStep().rewardMoney, player);
				reward += " " + getCurrentStep().rewardMoney + " deniers";
			}

			if (getCurrentStep().rewardReputation > 0) {
				mw.getProfile(player.getName()).adjustReputation(villager.getTownHall(), getCurrentStep().rewardReputation);

				reward += " " + getCurrentStep().rewardReputation + " reputation";

				int experience = getCurrentStep().rewardReputation / 32;

				if (experience > 16) {
					experience = 16;
				}

				if (experience > 0) {
					reward += " " + experience + " experience";
					MillCommonUtilities.spawnExp(world, villager.getPos().getRelative(0, 2, 0), experience);
				}
			}

			mw.getProfile(player.getName()).adjustLanguage(villager.getCulture().key, QUEST_LANGUAGE_BONUS);

			if (!world.isRemote) {

				applyTags(getCurrentStep().setVillagerTagsSuccess, getCurrentStep().clearTagsSuccess);
				applyGlobalTags(getCurrentStep().setGlobalTagsSuccess, getCurrentStep().clearGlobalTagsSuccess);
				applyPlayerTags(getCurrentStep().setPlayerTagsSuccess, getCurrentStep().clearPlayerTagsSuccess);
				applyActionData(getCurrentStep().setActionDataSuccess);

				for (final String s : getCurrentStep().bedrockbuildings) {
					final String culture = s.split(",")[0];
					final String village = s.split(",")[1];

					final VillageType vt = Culture.getCultureByName(culture).getLoneBuildingType(village);

					try {
						WorldGenVillage.generateBedrockLoneBuilding(new Point(player), world, vt, MillCommonUtilities.random, 50, 120, player);
					} catch (final MillenaireException e) {
						MLN.printException(e);
					}
				}
			}

			String res = getDescriptionSuccess(mw.getProfile(player.getName()));

			if (reward.length() > 0) {
				res += "<ret><ret>" + MLN.string("quest.obtained") + ":" + reward;
			}

			currentStep++;
			if (currentStep >= quest.steps.size()) {
				player.addStat(MillAchievements.thequest, 1);

				if (mw.getProfile(player.getName()).isWorldQuestFinished()) {
					player.addStat(MillAchievements.forbiddenknwoledge, 1);
				}

				destroySelf();
			} else {
				currentStepStart = villager.worldObj.getWorldTime();
				profile.sendQuestInstancePacket(this);
				profile.saveQuestInstances();
			}

			return res;
		}

		private void destroySelf() {
			profile.questInstances.remove(this);
			for (final QuestInstanceVillager qiv : villagers.values()) {
				profile.villagersInQuests.remove(qiv.id);
			}
			profile.saveQuestInstances();
		}

		public QuestStep getCurrentStep() {
			return quest.steps.get(currentStep);
		}

		public QuestInstanceVillager getCurrentVillager() {
			return villagers.get(getCurrentStep().villager);
		}

		public String getDescription(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getDescription());
		}

		public String getDescriptionRefuse(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getDescriptionRefuse());
		}

		public String getDescriptionSuccess(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getDescriptionSuccess());
		}

		public String getDescriptionTimeUp(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getDescriptionTimeUp());
		}

		public String getLabel(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getLabel());
		}

		public String getListing(final UserProfile profile) {
			return handleString(profile, getCurrentStep().getListing());
		}

		public QuestStep getNextStep() {
			if (currentStep + 1 < quest.steps.size()) {
				return quest.steps.get(currentStep + 1);
			}
			return null;
		}

		public QuestStep getPreviousStep() {
			if (currentStep > 0) {
				return quest.steps.get(currentStep - 1);
			}
			return null;
		}

		private String handleString(final UserProfile profile, String s) {

			if (s == null) {
				return null;
			}

			final Building giverTH = villagers.get(getCurrentStep().villager).getTownHall(world);

			if (giverTH == null) {
				return s;
			}

			for (final String key : villagers.keySet()) {
				final QuestInstanceVillager qiv = villagers.get(key);
				final Building th = qiv.getTownHall(world);

				if (th != null) {
					s = s.replaceAll("\\$" + key + "_villagename\\$", th.getVillageQualifiedName());
					s = s.replaceAll("\\$" + key + "_direction\\$", MLN.string(giverTH.getPos().directionTo(th.getPos())));
					s = s.replaceAll("\\$" + key + "_tothedirection\\$", MLN.string(giverTH.getPos().directionTo(th.getPos(), true)));
					s = s.replaceAll("\\$" + key + "_directionshort\\$", MLN.string(giverTH.getPos().directionToShort(th.getPos())));
					s = s.replaceAll("\\$" + key + "_distance\\$", MLN.string(giverTH.getPos().approximateDistanceLongString(th.getPos())));
					s = s.replaceAll("\\$" + key + "_distanceshort\\$", MLN.string(giverTH.getPos().approximateDistanceShortString(th.getPos())));

					final VillagerRecord villager = qiv.getVillagerRecord(world);

					if (villager != null) {
						s = s.replaceAll("\\$" + key + "_villagername\\$", villager.getName());
						s = s.replaceAll("\\$" + key + "_villagerrole\\$", villager.getGameOccupation(profile.key));
					}

					for (final String key2 : villagers.keySet()) {
						final QuestInstanceVillager qiv2 = villagers.get(key2);
						final Building th2 = qiv2.getTownHall(world);

						if (th2 != null) {
							s = s.replaceAll("\\$" + key + "_" + key2 + "_direction\\$", MLN.string(th.getPos().directionTo(th2.getPos())));
							s = s.replaceAll("\\$" + key + "_" + key2 + "_directionshort\\$", MLN.string(th.getPos().directionToShort(th2.getPos())));
							s = s.replaceAll("\\$" + key + "_" + key2 + "_distance\\$", MLN.string(th.getPos().approximateDistanceLongString(th2.getPos())));
							s = s.replaceAll("\\$" + key + "_" + key2 + "_distanceshort\\$", MLN.string(th.getPos().approximateDistanceShortString(th2.getPos())));
						} else {// can happen in MP
							s = s.replaceAll("\\$" + key + "_" + key2 + "_direction\\$", "");
							s = s.replaceAll("\\$" + key + "_" + key2 + "_directionshort\\$", "");
							s = s.replaceAll("\\$" + key + "_" + key2 + "_distance\\$", "");
							s = s.replaceAll("\\$" + key + "_" + key2 + "_distanceshort\\$", "");
						}
					}
				}
			}

			s = s.replaceAll("\\$name", profile.playerName);

			return s;
		}

		public String refuseQuest(final EntityPlayer player, final MillVillager villager) {

			String replost = "";

			final MillVillager cv = getCurrentVillager().getVillager(world);
			if (cv != null && getCurrentStep().penaltyReputation > 0) {
				mw.getProfile(player.getName()).adjustReputation(cv.getTownHall(), -getCurrentStep().penaltyReputation);
				replost = " (Reputation lost: " + getCurrentStep().penaltyReputation + ")";
			}

			applyTags(getCurrentStep().setVillagerTagsFailure, getCurrentStep().clearTagsFailure);
			applyPlayerTags(getCurrentStep().setPlayerTagsFailure, getCurrentStep().clearPlayerTagsFailure);
			applyGlobalTags(getCurrentStep().setGlobalTagsFailure, getCurrentStep().clearGlobalTagsFailure);

			final String s = getDescriptionRefuse(mw.getProfile(player.getName())) + replost;

			destroySelf();

			return s;
		}

		@Override
		public String toString() {
			return "QI:" + quest.key;
		}

		public String writeToString() {
			String s = "quest:" + quest.key + ";step:" + this.currentStep + ";startTime:" + startTime + ";currentStepStartTime:" + currentStepStart;

			for (final String key : villagers.keySet()) {
				final QuestInstanceVillager qiv = villagers.get(key);
				s += ";villager:" + key + "," + qiv.id + "," + qiv.townHall;
			}
			return s;
		}

	}

	public static class QuestInstanceVillager {

		public long id;
		public Point townHall;
		private MillVillager villager = null;
		private VillagerRecord vr = null;
		public MillWorld mw;

		public QuestInstanceVillager(final MillWorld mw, final Point p, final long vid) {
			townHall = p;
			id = vid;
			this.mw = mw;
		}

		public QuestInstanceVillager(final MillWorld mw, final Point p, final long vid, final MillVillager v) {
			townHall = p;
			id = vid;
			villager = v;
			this.mw = mw;
		}

		public QuestInstanceVillager(final MillWorld mw, final Point p, final long vid, final VillagerRecord v) {
			townHall = p;
			id = vid;
			vr = v;
			this.mw = mw;
		}

		public Building getTownHall(final World world) {
			return mw.getBuilding(townHall);
		}

		public MillVillager getVillager(final World world) {
			if (villager == null) {
				final Building th = mw.getBuilding(townHall);
				if (th != null) {
					villager = th.getVillagerById(id);
				}
			}
			return villager;
		}

		public VillagerRecord getVillagerRecord(final World world) {
			if (vr == null) {
				final Building th = mw.getBuilding(townHall);
				if (th != null) {
					vr = th.getVillagerRecordById(id);
				}
			}
			return vr;
		}
	}

	public class QuestStep {

		int pos;

		public List<String> clearGlobalTagsFailure = new ArrayList<String>();
		public List<String> clearGlobalTagsSuccess = new ArrayList<String>();
		public List<String> clearPlayerTagsFailure = new ArrayList<String>();
		public List<String> clearPlayerTagsSuccess = new ArrayList<String>();
		public List<String[]> clearTagsFailure = new ArrayList<String[]>();
		public List<String[]> clearTagsSuccess = new ArrayList<String[]>();

		public final HashMap<String, String> descriptions = new HashMap<String, String>();
		public final HashMap<String, String> descriptionsRefuse = new HashMap<String, String>();
		public final HashMap<String, String> descriptionsSuccess = new HashMap<String, String>();
		public final HashMap<String, String> descriptionsTimeUp = new HashMap<String, String>();
		public final HashMap<String, String> labels = new HashMap<String, String>();
		public final HashMap<String, String> listings = new HashMap<String, String>();

		public int duration = 1;
		public List<String> forbiddenGlobalTag = new ArrayList<String>();
		public List<String> forbiddenPlayerTag = new ArrayList<String>();

		public int penaltyReputation = 0;
		public HashMap<InvItem, Integer> requiredGood = new HashMap<InvItem, Integer>();
		public List<String> stepRequiredGlobalTag = new ArrayList<String>();
		public List<String> stepRequiredPlayerTag = new ArrayList<String>();
		public HashMap<InvItem, Integer> rewardGoods = new HashMap<InvItem, Integer>();
		public int rewardMoney = 0;
		public int rewardReputation = 0;

		public List<String> bedrockbuildings = new ArrayList<String>();

		public List<String> setGlobalTagsFailure = new ArrayList<String>();
		public List<String> setGlobalTagsSuccess = new ArrayList<String>();

		public List<String> setPlayerTagsFailure = new ArrayList<String>();
		public List<String> setPlayerTagsSuccess = new ArrayList<String>();

		public List<String[]> setVillagerTagsFailure = new ArrayList<String[]>();
		public List<String[]> setVillagerTagsSuccess = new ArrayList<String[]>();

		public List<String[]> setActionDataSuccess = new ArrayList<String[]>();

		public boolean showRequiredGoods = true;
		public String villager;

		public QuestStep(final int pos) {
			this.pos = pos;
		}

		public String getDescription() {
			return MLN.questString(getStringKey() + "description", true);
		}

		public String getDescriptionRefuse() {
			return MLN.questString(getStringKey() + "description_refuse", true);
		}

		public String getDescriptionSuccess() {
			return MLN.questString(getStringKey() + "description_success", true);
		}

		public String getDescriptionTimeUp() {
			return MLN.questString(getStringKey() + "description_timeup", false);
		}

		public String getLabel() {
			return MLN.questString(getStringKey() + "label", true);
		}

		public String getListing() {
			return MLN.questString(getStringKey() + "listing", false);
		}

		public String getStringKey() {
			return key + "_" + pos + "_";
		}

		public String lackingConditions(final EntityPlayer player) {

			final MillWorld mw = Mill.getMillWorld(player.worldObj);

			final UserProfile profile = mw.getProfile(player.getName());

			String lackingGoods = null;
			for (final InvItem item : requiredGood.keySet()) {

				int diff;

				if (item.special == InvItem.ANYENCHANTED) {
					int nbenchanted = 0;

					for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
						final ItemStack stack = player.inventory.getStackInSlot(i);

						if (stack != null && stack.isItemEnchanted()) {
							nbenchanted++;
						}
					}
					diff = requiredGood.get(item) - nbenchanted;
				} else if (item.special == InvItem.ENCHANTEDSWORD) {
					int nbenchanted = 0;

					for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
						final ItemStack stack = player.inventory.getStackInSlot(i);

						if (stack != null && stack.isItemEnchanted() && stack.getItem() instanceof ItemSword) {
							nbenchanted++;
						}
					}
					diff = requiredGood.get(item) - nbenchanted;
				} else {
					diff = requiredGood.get(item) - MillCommonUtilities.countChestItems(player.inventory, item.getItem(), item.meta);
				}

				if (diff > 0) {
					if (lackingGoods != null) {
						lackingGoods += ", ";
					} else {
						lackingGoods = "";
					}
					lackingGoods += diff + " " + item.getName();
				}
			}
			if (lackingGoods != null) {
				if (showRequiredGoods) {
					lackingGoods = MLN.string("quest.lackingcondition") + " " + lackingGoods;
				} else {
					lackingGoods = MLN.string("quest.lackinghiddengoods");
				}
			}

			boolean tagsOk = true;
			for (final String tag : stepRequiredGlobalTag) {
				if (!mw.isGlobalTagSet(tag)) {
					tagsOk = false;
				}
			}
			for (final String tag : forbiddenGlobalTag) {
				if (mw.isGlobalTagSet(tag)) {
					tagsOk = false;
				}
			}
			for (final String tag : stepRequiredPlayerTag) {
				if (!profile.isTagSet(tag)) {
					tagsOk = false;
				}
			}
			for (final String tag : forbiddenPlayerTag) {
				if (profile.isTagSet(tag)) {
					tagsOk = false;
				}
			}

			if (!tagsOk) {
				if (lackingGoods != null) {
					lackingGoods += ". ";
				} else {
					lackingGoods = "";
				}
				lackingGoods += MLN.string("quest.conditionsnotmet");
			}

			return lackingGoods;
		}
	}

	public class QuestVillager {
		List<String> forbiddenTags = new ArrayList<String>();
		String key = null, relatedto = null, relation = null;
		List<String> requiredTags = new ArrayList<String>();
		List<String> types = new ArrayList<String>();

		public boolean testVillager(final UserProfile profile, final VillagerRecord vr) {

			if (profile.villagersInQuests.containsKey(vr.id)) {
				return false;
			}

			if (!types.isEmpty() && !types.contains(vr.type)) {
				return false;
			}

			for (final String tag : requiredTags) {
				final String tagPlayer = profile.key + "_" + tag;

				if (!vr.questTags.contains(tagPlayer)) {
					return false;
				}
			}

			for (final String tag : forbiddenTags) {
				final String tagPlayer = profile.key + "_" + tag;

				if (vr.questTags.contains(tagPlayer)) {
					return false;
				}
			}

			for (final String tag : vr.questTags) {

				final String tagPlayer = profile.key + "_" + tag;

				if (forbiddenTags.contains(tagPlayer)) {
					return false;
				}
			}

			return true;
		}
	}

	public static HashMap<String, Quest> quests = new HashMap<String, Quest>();
	private static final String REL_NEARBYVILLAGE = "nearbyvillage";

	private static final String REL_ANYVILLAGE = "anyvillage";
	private static final String REL_SAMEHOUSE = "samehouse";

	private static final String REL_SAMEVILLAGE = "samevillage";

	public static final int[] WORLD_MISSION_NB = new int[] { 15, 13, 10 };
	public static final String[] WORLD_MISSION_KEYS = new String[] { "sadhu", "alchemist", "fallenking" };

	private static Quest loadQuest(final File file) {

		final Quest q = new Quest();

		q.key = file.getName().split("\\.")[0];

		try {
			final BufferedReader reader = MillCommonUtilities.getReader(file);

			String line;
			QuestStep step = null;

			while ((line = reader.readLine()) != null) {
				if (line.trim().length() > 0 && !line.startsWith("//")) {
					final String[] temp = line.split(":");
					if (temp.length != 2) {
						MLN.error(null, "Invalid line when loading quest " + file.getName() + ": " + line);
					} else {
						final String key = temp[0].toLowerCase();
						final String value = temp[1];
						if (key.equals("step")) {
							step = q.new QuestStep(q.steps.size());
							q.steps.add(step);
						} else if (key.equals("minreputation")) {
							q.minreputation = MillCommonUtilities.readInteger(value);
						} else if (key.equals("chanceperhour")) {
							q.chanceperhour = Double.parseDouble(value);
						} else if (key.equals("maxsimultaneous")) {
							q.maxsimultaneous = MillCommonUtilities.readInteger(value);
						} else if (key.equals("definevillager")) {
							final QuestVillager v = q.loadQVillager(value);
							if (v != null) {
								q.villagers.put(v.key, v);
								q.villagersOrdered.add(v);
							}
						} else if (key.equals("requiredglobaltag")) {
							q.globalTagsRequired.add(value.trim().toLowerCase());
						} else if (key.equals("forbiddenglobaltag")) {
							q.globalTagsForbidden.add(value.trim().toLowerCase());
						} else if (key.equals("requiredplayertag")) {
							q.profileTagsRequired.add(value.trim().toLowerCase());
						} else if (key.equals("forbiddenplayertag")) {
							q.profileTagsForbidden.add(value.trim().toLowerCase());
						} else if (step == null) {
							MLN.error(q, "Reached line while not in a step: " + line);
						} else if (key.equals("villager")) {
							step.villager = value;
						} else if (key.equals("duration")) {
							step.duration = MillCommonUtilities.readInteger(value);
						} else if (key.equals("showrequiredgoods")) {
							step.showRequiredGoods = Boolean.parseBoolean(value);
						} else if (key.startsWith("label_")) {
							step.labels.put(key, value);
						} else if (key.startsWith("description_success_")) {
							step.descriptionsSuccess.put(key, value);
						} else if (key.startsWith("description_refuse_")) {
							step.descriptionsRefuse.put(key, value);
						} else if (key.startsWith("description_timeup_")) {
							step.descriptionsTimeUp.put(key, value);
						} else if (key.startsWith("description_")) {
							step.descriptions.put(key, value);
						} else if (key.startsWith("listing_")) {
							step.listings.put(key, value);
						} else if (key.equals("requiredgood")) {
							if (Goods.goodsName.containsKey(value.split(",")[0].toLowerCase())) {
								final InvItem iv = Goods.goodsName.get(value.split(",")[0].toLowerCase());
								step.requiredGood.put(iv, MillCommonUtilities.readInteger(value.split(",")[1]));
							} else {
								MLN.error(null, "Unknown requiredgood found when loading quest " + file.getName() + ": " + value);
							}
						} else if (key.equals("rewardgood")) {
							if (Goods.goodsName.containsKey(value.split(",")[0].toLowerCase())) {
								final InvItem iv = Goods.goodsName.get(value.split(",")[0].toLowerCase());
								step.rewardGoods.put(iv, MillCommonUtilities.readInteger(value.split(",")[1]));
							} else {
								MLN.error(null, "Unknown rewardGood found when loading quest " + file.getName() + ": " + value);
							}
						} else if (key.equals("rewardmoney")) {
							step.rewardMoney = MillCommonUtilities.readInteger(value);
						} else if (key.equals("rewardreputation")) {
							step.rewardReputation = MillCommonUtilities.readInteger(value);
						} else if (key.equals("penaltyreputation")) {
							step.penaltyReputation = MillCommonUtilities.readInteger(value);
						} else if (key.equals("setactiondatasuccess")) {
							step.setActionDataSuccess.add(value.split(","));

						} else if (key.equals("settagsuccess")) {
							step.setVillagerTagsSuccess.add(value.split(","));
						} else if (key.equals("cleartagsuccess")) {
							step.clearTagsSuccess.add(value.split(","));
						} else if (key.equals("settagfailure")) {
							step.setVillagerTagsFailure.add(value.split(","));
						} else if (key.equals("cleartagfailure")) {
							step.clearTagsFailure.add(value.split(","));

						} else if (key.equals("setglobaltagsuccess")) {
							step.setGlobalTagsSuccess.add(value);
						} else if (key.equals("clearglobaltagsuccess")) {
							step.clearGlobalTagsSuccess.add(value);
						} else if (key.equals("setglobaltagfailure")) {
							step.setGlobalTagsFailure.add(value);
						} else if (key.equals("clearglobaltagfailure")) {
							step.clearGlobalTagsFailure.add(value);

						} else if (key.equals("setplayertagsuccess")) {
							step.setPlayerTagsSuccess.add(value);
						} else if (key.equals("clearplayertagsuccess")) {
							step.clearPlayerTagsSuccess.add(value);
						} else if (key.equals("setplayertagfailure")) {
							step.setPlayerTagsFailure.add(value);
						} else if (key.equals("clearplayertagfailure")) {
							step.clearPlayerTagsFailure.add(value);

						} else if (key.equals("steprequiredglobaltag")) {
							step.stepRequiredGlobalTag.add(value);
						} else if (key.equals("stepforbiddenglobaltag")) {
							step.forbiddenGlobalTag.add(value);
						} else if (key.equals("steprequiredplayertag")) {
							step.stepRequiredPlayerTag.add(value);
						} else if (key.equals("stepforbiddenplayertag")) {
							step.forbiddenPlayerTag.add(value);

						} else if (key.equals("bedrockbuilding")) {
							step.bedrockbuildings.add(value.trim().toLowerCase());
						} else {
							MLN.error(null, "Unknow parameter when loading quest " + file.getName() + ": " + line);
						}
					}
				}
			}
			reader.close();

			if (q.steps.size() == 0) {
				MLN.error(q, "No steps found in " + file.getName() + ".");
				return null;
			}

			if (q.villagersOrdered.size() == 0) {
				MLN.error(q, "No villagers defined in " + file.getName() + ".");
				return null;
			}

			if (MLN.LogQuest >= MLN.MAJOR) {
				MLN.major(q, "Loaded quest type: " + q.key);
			}

			return q;

		} catch (final Exception e) {
			MLN.printException(e);
		}
		return null;
	}

	public static void loadQuests() {

		final List<File> questDirs = new ArrayList<File>();

		for (final File dir : Mill.loadingDirs) {
			final File questDir = new File(dir, "quests");

			if (questDir.exists()) {
				questDirs.add(questDir);
			}
		}

		final File customQuestDir = new File(Mill.proxy.getCustomDir(), "quests");

		if (customQuestDir.exists()) {
			questDirs.add(customQuestDir);
		}

		for (final File questdir : questDirs) {

			for (final File dir : questdir.listFiles()) {
				if (dir.isDirectory()) {
					for (final File file : dir.listFiles(new ExtFileFilter("txt"))) {

						final Quest quest = loadQuest(file);

						if (quest != null) {
							quests.put(quest.key, quest);
						}
					}
				}
			}
		}

		if (MLN.LogQuest >= MLN.MAJOR) {
			MLN.major(null, "Loaded " + quests.size() + " quests.");
		}
	}

	public double chanceperhour = 0;

	public String key;

	public int maxsimultaneous = 5;

	public int minreputation = 0;

	public List<QuestStep> steps = new ArrayList<QuestStep>();

	public List<String> globalTagsForbidden = new ArrayList<String>();
	public List<String> globalTagsRequired = new ArrayList<String>();

	public List<String> profileTagsForbidden = new ArrayList<String>();
	public List<String> profileTagsRequired = new ArrayList<String>();

	public HashMap<String, QuestVillager> villagers = new HashMap<String, QuestVillager>();

	public List<QuestVillager> villagersOrdered = new ArrayList<QuestVillager>();

	private QuestVillager loadQVillager(final String line) {
		final QuestVillager v = this.new QuestVillager();
		for (final String s : line.split(",")) {
			final String key = s.split("=")[0].toLowerCase();
			final String val = s.split("=")[1];

			if (key.equals("key")) {
				v.key = val;
			} else if (key.equals("type")) {
				final Culture c = Culture.getCultureByName(val.split("/")[0]);
				if (c == null) {
					MLN.error(this, "Unknow culture when loading definevillager: " + line);
					return null;
				} else {
					final VillagerType vtype = c.getVillagerType(val.split("/")[1]);
					if (vtype == null) {
						MLN.error(this, "Unknow vilager type when loading definevillager: " + line);
						return null;
					} else {
						v.types.add(vtype.key);
					}
				}
			} else if (key.equals("relatedto")) {
				v.relatedto = val;
			} else if (key.equals("relation")) {
				v.relation = val;
			} else if (key.equals("forbiddentag")) {
				v.forbiddenTags.add(val);
			} else if (key.equals("requiredtag")) {
				v.requiredTags.add(val);
			}
		}

		if (v.key == null) {
			MLN.error(this, "No key found when loading definevillager: " + line);
			return null;
		}

		return v;
	}

	public QuestInstance testQuest(final MillWorld mw, final UserProfile profile) {

		if (!MillCommonUtilities.probability(chanceperhour)) {
			return null;
		}

		int nb = 0;

		for (final QuestInstance qi : profile.questInstances) {
			if (qi.quest == this) {
				nb++;
			}
		}

		if (nb >= maxsimultaneous) {
			return null;
		}

		for (final String tag : globalTagsRequired) {
			if (!mw.isGlobalTagSet(tag)) {
				return null;
			}
		}

		for (final String tag : profileTagsRequired) {
			if (!profile.isTagSet(tag)) {
				return null;
			}
		}

		for (final String tag : globalTagsForbidden) {
			if (mw.isGlobalTagSet(tag)) {
				return null;
			}
		}

		for (final String tag : profileTagsForbidden) {
			if (profile.isTagSet(tag)) {
				return null;
			}
		}

		if (MLN.LogQuest >= MLN.DEBUG) {
			MLN.debug(this, "Testing quest " + key);
		}

		final QuestVillager startingVillager = villagersOrdered.get(0);

		final List<HashMap<String, QuestInstanceVillager>> possibleVillagers = new ArrayList<HashMap<String, QuestInstanceVillager>>();

		for (final Point p : mw.getCombinedVillagesLoneBuildings()) {
			final Building th = mw.getBuilding(p);
			if (th != null && th.isActive && th.getReputation(profile.key) >= minreputation) {

				if (MLN.LogQuest >= MLN.DEBUG) {
					MLN.debug(this, "Loooking for starting villager in: " + th.getVillageQualifiedName());
				}

				for (final VillagerRecord vr : th.vrecords) {
					if (startingVillager.testVillager(profile, vr)) {
						final HashMap<String, QuestInstanceVillager> villagers = new HashMap<String, QuestInstanceVillager>();
						villagers.put(startingVillager.key, new QuestInstanceVillager(mw, p, vr.id, vr));

						boolean error = false;

						if (MLN.LogQuest >= MLN.DEBUG) {
							MLN.debug(this, "Found possible starting villager: " + vr);
						}

						for (final QuestVillager qv : villagersOrdered) {
							if (!error && qv != startingVillager) {

								if (MLN.LogQuest >= MLN.DEBUG) {
									MLN.debug(this, "Trying to find villager type: " + qv.relation + "/" + qv.relatedto);
								}

								final VillagerRecord relatedVillager = villagers.get(qv.relatedto).getVillagerRecord(mw.world);

								if (relatedVillager == null) {
									error = true;
									break;
								}

								if (REL_SAMEVILLAGE.equals(qv.relation)) {
									final List<VillagerRecord> newVillagers = new ArrayList<VillagerRecord>();
									for (final VillagerRecord vr2 : mw.getBuilding(relatedVillager.townHallPos).vrecords) {
										if (!vr2.housePos.equals(relatedVillager.housePos) && qv.testVillager(profile, vr2)) {
											newVillagers.add(vr2);
										}
									}

									if (newVillagers.size() > 0) {
										final VillagerRecord chosen = newVillagers.get(MillCommonUtilities.randomInt(newVillagers.size()));
										villagers.put(qv.key, new QuestInstanceVillager(mw, p, chosen.id, chosen));
									} else {
										error = true;
									}
								} else if (REL_NEARBYVILLAGE.equals(qv.relation) || REL_ANYVILLAGE.equals(qv.relation)) {
									final List<QuestInstanceVillager> newVillagers = new ArrayList<QuestInstanceVillager>();

									for (final Point p2 : mw.getCombinedVillagesLoneBuildings()) {
										final Building th2 = mw.getBuilding(p2);
										if (th2 != null && th2 != th && (REL_ANYVILLAGE.equals(qv.relation) || th.getPos().distanceTo(th2.getPos()) < 2000)) {

											if (MLN.LogQuest >= MLN.DEBUG) {
												MLN.debug(this, "Trying to find villager type: " + qv.relation + "/" + qv.relatedto + " in " + th2.getVillageQualifiedName());
											}

											for (final VillagerRecord vr2 : th2.vrecords) {

												if (MLN.LogQuest >= MLN.DEBUG) {
													MLN.debug(this, "Testing: " + vr2);
												}

												if (qv.testVillager(profile, vr2)) {
													newVillagers.add(new QuestInstanceVillager(mw, p2, vr2.id, vr2));
												}
											}
										}
									}

									if (newVillagers.size() > 0) {
										villagers.put(qv.key, newVillagers.get(MillCommonUtilities.randomInt(newVillagers.size())));
									} else {
										error = true;
									}
								} else if (REL_SAMEHOUSE.equals(qv.relation)) {
									final List<VillagerRecord> newVillagers = new ArrayList<VillagerRecord>();
									for (final VillagerRecord vr2 : mw.getBuilding(relatedVillager.townHallPos).vrecords) {
										if (vr2.housePos.equals(relatedVillager.housePos) && qv.testVillager(profile, vr2)) {
											newVillagers.add(vr2);
										}
									}

									if (newVillagers.size() > 0) {
										final VillagerRecord chosen = newVillagers.get(MillCommonUtilities.randomInt(newVillagers.size()));
										villagers.put(qv.key, new QuestInstanceVillager(mw, p, chosen.id, chosen));
									} else {
										error = true;
									}
								} else {
									MLN.error(this, "Unknown relation: " + qv.relation);
								}
							}
						}

						if (!error) {
							possibleVillagers.add(villagers);
							if (MLN.LogQuest >= MLN.DEBUG) {
								MLN.debug(this, "Found all the villagers needed: " + villagers.size());
							}
						}
					}
				}
			}
		}

		if (possibleVillagers.isEmpty()) {
			return null;
		}

		final HashMap<String, QuestInstanceVillager> selectedOption = possibleVillagers.get(MillCommonUtilities.randomInt(possibleVillagers.size()));

		final QuestInstance qi = new QuestInstance(mw, this, profile, selectedOption, mw.world.getWorldTime());

		profile.questInstances.add(qi);

		for (final QuestInstanceVillager qiv : selectedOption.values()) {
			profile.villagersInQuests.put(qiv.id, qi);
		}

		return qi;

	}

	@Override
	public String toString() {
		return "QT: " + key;
	}
}
