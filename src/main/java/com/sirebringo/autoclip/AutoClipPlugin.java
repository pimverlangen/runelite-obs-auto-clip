/*
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sirebringo.autoclip;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.message.event.outputs.ReplayBufferSavedEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.widgets.WidgetID.*;

@PluginDescriptor(
        name = "OBS Auto Clip",
        description = "Enable the manual and automatic saving of the replay buffer from OBS",
        tags = {"external", "clips", "imgur", "integration", "notifications"}
)
@Slf4j
public class AutoClipPlugin extends Plugin {
    private static final String COLLECTION_LOG_TEXT = "New item added to your collection log: ";
    private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
    private static final Map<Integer, String> CHEST_LOOT_EVENTS = ImmutableMap.of(12127, "The Gauntlet");
    private static final int GAUNTLET_REGION = 7512;
    private static final int CORRUPTED_GAUNTLET_REGION = 7768;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
    private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");
    private static final Pattern BOSSKILL_MESSAGE_PATTERN = Pattern.compile("Your (.+) kill count is: <col=ff0000>(\\d+)</col>.");
    private static final Pattern VALUABLE_DROP_PATTERN = Pattern.compile(".*Valuable drop: ([^<>]+?\\(((?:\\d+,?)+) coins\\))(?:</col>)?");
    private static final Pattern UNTRADEABLE_DROP_PATTERN = Pattern.compile(".*Untradeable drop: ([^<>]+)(?:</col>)?");
    private static final Pattern DUEL_END_PATTERN = Pattern.compile("You have now (won|lost) ([0-9,]+) duels?\\.");
    private static final Pattern QUEST_PATTERN_1 = Pattern.compile(".+?ve\\.*? (?<verb>been|rebuilt|.+?ed)? ?(?:the )?'?(?<quest>.+?)'?(?: [Qq]uest)?[!.]?$");
    private static final Pattern QUEST_PATTERN_2 = Pattern.compile("'?(?<quest>.+?)'?(?: [Qq]uest)? (?<verb>[a-z]\\w+?ed)?(?: f.*?)?[!.]?$");
    private static final Pattern COMBAT_ACHIEVEMENTS_PATTERN = Pattern.compile("Congratulations, you've completed an? (?<tier>\\w+) combat task: <col=[0-9a-f]+>(?<task>(.+))</col>\\.");
    private static final ImmutableList<String> RFD_TAGS = ImmutableList.of("Another Cook", "freed", "defeated", "saved");
    private static final ImmutableList<String> WORD_QUEST_IN_NAME_TAGS = ImmutableList.of("Another Cook", "Doric", "Heroes", "Legends", "Observatory", "Olaf", "Waterfall");
    private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of("You have a funny feeling like you're being followed",
            "You feel something weird sneaking into your backpack",
            "You have a funny feeling like you would have been followed");
    private static final Pattern BA_HIGH_GAMBLE_REWARD_PATTERN = Pattern.compile("(?<reward>.+)!<br>High level gamble count: <col=7f0000>(?<gambleCount>.+)</col>");
    private static final String SD_KINGDOM_REWARDS = "Kingdom Rewards";
    private static final String SD_BOSS_KILLS = "Boss Kills";
    private static final String SD_CLUE_SCROLL_REWARDS = "Clue Scroll Rewards";
    private static final String SD_PETS = "Pets";
    private static final String SD_CHEST_LOOT = "Chest Loot";
    private static final String SD_VALUABLE_DROPS = "Valuable Drops";
    private static final String SD_UNTRADEABLE_DROPS = "Untradeable Drops";
    private static final String SD_DUELS = "Duels";
    private static final String SD_COLLECTION_LOG = "Collection Log";
    private static final String SD_PVP_KILLS = "PvP Kills";
    private static final String SD_DEATHS = "Deaths";
    private static final String SD_COMBAT_ACHIEVEMENTS = "Combat Achievements";

    private String clueType;
    private Integer clueNumber;

    enum KillType {
        BARROWS,
        COX,
        COX_CM,
        TOB,
        TOB_SM,
        TOB_HM,
        TOA_ENTRY_MODE,
        TOA,
        TOA_EXPERT_MODE
    }

    private KillType killType;
    private Integer killCountNumber;

    private boolean shouldTakeClip;
    private boolean notificationStarted;

    private OBSRemoteController obsController;
    private JsonObject baseObsOutputSettings;

    private final String OBS_BASE_FORMAT = "%CCYY-%MM-%DD %hh-%mm-%ss";
    private long replayBufferDuration = -1; // lazy init, see onOBSWSReady

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @Inject
    private AutoClipConfig config;

    @Inject
    private Client client;

    @Inject
    private ClientUI clientUi;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private DrawManager drawManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private KeyManager keyManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private ImageCapture imageCapture;

    @Inject
    private Notifier notifier;

    @Getter(AccessLevel.PACKAGE)
    private BufferedImage reportButton;

    private NavigationButton titleBarButton;

    private String kickPlayerName;

    private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.clipHotkey()) {
        @Override
        public void hotkeyPressed() {
            manualScreenshot();
        }
    };

    @Provides
    AutoClipConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoClipConfig.class);
    }

    @Override
    protected void startUp() {
        obsController = OBSRemoteController
                .builder()
                .host(config.obsServerHost())
                .port(config.obsServerPort())
                .password(config.obsServerPassword())
                .registerEventListener(
                        ReplayBufferSavedEvent.class, this::onSuccessfulSave
                )
                .lifecycle()
                .onReady(this::onOBSWSReady)
                .and()
                .autoConnect(true)
                .build();
        keyManager.registerKeyListener(hotkeyListener);
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(titleBarButton);
        keyManager.unregisterKeyListener(hotkeyListener);
        kickPlayerName = null;
        notificationStarted = false;
        obsController.disconnect();
        obsController.stop();
        obsController = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!shouldTakeClip) {
            return;
        }

        shouldTakeClip = false;
        String clipSubDir = null;

        String fileName = null;
        if (client.getWidget(WidgetInfo.LEVEL_UP_LEVEL) != null) {
            fileName = parseLevelUpWidget(WidgetInfo.LEVEL_UP_LEVEL);
            clipSubDir = "Levels";
        } else if (client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT) != null) {
            String text = client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT).getText();
            if (Text.removeTags(text).contains("High level gamble")) {
                if (config.clipHighGamble()) {
                    fileName = parseBAHighGambleWidget(text);
                    clipSubDir = "BA High Gambles";
                }
            } else {
                if (config.clipLevels()) {
                    fileName = parseLevelUpWidget(WidgetInfo.DIALOG_SPRITE_TEXT);
                    clipSubDir = "Levels";
                }
            }
        } else if (client.getWidget(WidgetInfo.QUEST_COMPLETED_NAME_TEXT) != null) {
            String text = client.getWidget(WidgetInfo.QUEST_COMPLETED_NAME_TEXT).getText();
            fileName = parseQuestCompletedWidget(text);
            clipSubDir = "Quests";
        }

        if (fileName != null) {
            startReplayBufferSave(fileName, clipSubDir);

            // this is a copy of the "clip" plugin they already had, but instead of saving a clip, I now want to
            // save the replay buffer of OBS


            // basically, this function should send a "save" request
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) { // this is where a player dies for instance
        Actor actor = actorDeath.getActor();
        if (actor instanceof Player) {
            Player player = (Player) actor;
            if (player == client.getLocalPlayer() && config.clipPlayerDeath()) {
                startReplayBufferSave("Deaths", SD_DEATHS);
            } else if (player != client.getLocalPlayer()
                    && player.getCanvasTilePoly() != null
                    && (((player.isFriendsChatMember() || player.isFriend()) && config.clipFriendDeath())
                    || (player.isClanMember() && config.clipClanDeath()))) {
                startReplayBufferSave("Death " + player.getName(), SD_DEATHS);
            }
        }
    }

    @Subscribe
    public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived) { // here a user gets some loot
        if (config.clipKills()) {
            final Player player = playerLootReceived.getPlayer();
            final String name = player.getName();
            String fileName = "Kill " + name;
            startReplayBufferSave(fileName, SD_PVP_KILLS);
        }
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent e) {
        if (!"confirmFriendsChatKick".equals(e.getEventName())) {
            return;
        }

        final String[] stringStack = client.getStringStack();
        final int stringSize = client.getStringStackSize();
        kickPlayerName = stringStack[stringSize - 1];
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
                && event.getType() != ChatMessageType.SPAM
                && event.getType() != ChatMessageType.TRADE
                && event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION) {
            return;
        }

        String chatMessage = event.getMessage();

        if (chatMessage.contains("You have completed") && chatMessage.contains("Treasure")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                clueNumber = Integer.valueOf(m.group());
                clueType = chatMessage.substring(chatMessage.lastIndexOf(m.group()) + m.group().length() + 1, chatMessage.indexOf("Treasure") - 1);
                return;
            }
        }

        if (chatMessage.startsWith("Your Barrows chest count is")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                killType = KillType.BARROWS;
                killCountNumber = Integer.valueOf(m.group());
                return;
            }
        }

        if (chatMessage.startsWith("Your completed Chambers of Xeric count is:")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                killType = KillType.COX;
                killCountNumber = Integer.valueOf(m.group());
                return;
            }
        }

        if (chatMessage.startsWith("Your completed Chambers of Xeric Challenge Mode count is:")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                killType = KillType.COX_CM;
                killCountNumber = Integer.valueOf(m.group());
                return;
            }
        }

        if (chatMessage.startsWith("Your completed Theatre of Blood")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                killType = chatMessage.contains("Hard Mode") ? KillType.TOB_HM : (chatMessage.contains("Story Mode") ? KillType.TOB_SM : KillType.TOB);
                killCountNumber = Integer.valueOf(m.group());
                return;
            }
        }

        if (chatMessage.startsWith("Your completed Tombs of Amascut")) {
            Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
            if (m.find()) {
                killType = chatMessage.contains("Expert Mode") ? KillType.TOA_EXPERT_MODE :
                        chatMessage.contains("Entry Mode") ? KillType.TOA_ENTRY_MODE :
                                KillType.TOA;
                killCountNumber = Integer.valueOf(m.group());
                return;
            }
        }

        if (config.clipPet() && PET_MESSAGES.stream().anyMatch(chatMessage::contains)) {
            String fileName = "Pet";
            startReplayBufferSave(fileName, SD_PETS);
        }

        if (config.clipBossKills()) {
            Matcher m = BOSSKILL_MESSAGE_PATTERN.matcher(chatMessage);
            if (m.matches()) {
                String bossName = m.group(1);
                String bossKillcount = m.group(2);
                String fileName = bossName + "(" + bossKillcount + ")";
                startReplayBufferSave(fileName, SD_BOSS_KILLS);
            }
        }

        if (chatMessage.equals(CHEST_LOOTED_MESSAGE) && config.clipRewards()) {
            final int regionID = client.getLocalPlayer().getWorldLocation().getRegionID();
            String eventName = CHEST_LOOT_EVENTS.get(regionID);
            if (eventName != null) {
                startReplayBufferSave(eventName, SD_CHEST_LOOT);
            }
        }

        if (config.clipValuableDrop()) {
            Matcher m = VALUABLE_DROP_PATTERN.matcher(chatMessage);
            if (m.matches()) {
                int valuableDropValue = Integer.parseInt(m.group(2).replaceAll(",", ""));
                if (valuableDropValue >= config.valuableDropThreshold()) {
                    String valuableDropName = m.group(1);
                    String fileName = "Valuable drop " + valuableDropName;
                    startReplayBufferSave(fileName, SD_VALUABLE_DROPS);
                }
            }
        }

        if (config.clipUntradeableDrop() && !isInsideGauntlet()) {
            Matcher m = UNTRADEABLE_DROP_PATTERN.matcher(chatMessage);
            if (m.matches()) {
                String untradeableDropName = m.group(1);
                String fileName = "Untradeable drop " + untradeableDropName;
                startReplayBufferSave(fileName, SD_UNTRADEABLE_DROPS);
            }
        }

        if (config.clipDuels()) {
            Matcher m = DUEL_END_PATTERN.matcher(chatMessage);
            if (m.find()) {
                String result = m.group(1);
                String count = m.group(2).replace(",", "");
                String fileName = "Duel " + result + " (" + count + ")";
                startReplayBufferSave(fileName, SD_DUELS);
            }
        }

        if (config.clipCollectionLogEntries() && chatMessage.startsWith(COLLECTION_LOG_TEXT) && client.getVarbitValue(Varbits.COLLECTION_LOG_NOTIFICATION) == 1) {
            String entry = Text.removeTags(chatMessage).substring(COLLECTION_LOG_TEXT.length());
            String fileName = "Collection log (" + entry + ")";
            startReplayBufferSave(fileName, SD_COLLECTION_LOG);
        }

        if (chatMessage.contains("combat task") && config.clipCombatAchievements() && client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENTS_POPUP) == 1) {
            String fileName = parseCombatAchievementWidget(chatMessage);
            if (!fileName.isEmpty()) {
                startReplayBufferSave(fileName, SD_COMBAT_ACHIEVEMENTS);
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        String fileName;
        String clipSubDir;
        int groupId = event.getGroupId();

        switch (groupId) {
            case QUEST_COMPLETED_GROUP_ID:
            case CLUE_SCROLL_REWARD_GROUP_ID:
            case CHAMBERS_OF_XERIC_REWARD_GROUP_ID:
            case THEATRE_OF_BLOOD_REWARD_GROUP_ID:
            case TOA_REWARD_GROUP_ID:
            case BARROWS_REWARD_GROUP_ID:
                if (!config.clipRewards()) {
                    return;
                }
                break;
            case LEVEL_UP_GROUP_ID:
                if (!config.clipLevels()) {
                    return;
                }
                break;
            case DIALOG_SPRITE_GROUP_ID:
                if (!(config.clipLevels() || config.clipHighGamble())) {
                    return;
                }
                break;
            case KINGDOM_GROUP_ID:
                if (!config.clipKingdom()) {
                    return;
                }
                break;
        }

        switch (groupId) {
            case KINGDOM_GROUP_ID: {
                fileName = "Kingdom " + LocalDate.now();
                clipSubDir = SD_KINGDOM_REWARDS;
                break;
            }
            case CHAMBERS_OF_XERIC_REWARD_GROUP_ID: {
                if (killType == KillType.COX) {
                    fileName = "Chambers of Xeric(" + killCountNumber + ")";
                    clipSubDir = SD_BOSS_KILLS;
                    killType = null;
                    killCountNumber = 0;
                    break;
                } else if (killType == KillType.COX_CM) {
                    fileName = "Chambers of Xeric Challenge Mode(" + killCountNumber + ")";
                    clipSubDir = SD_BOSS_KILLS;
                    killType = null;
                    killCountNumber = 0;
                    break;
                }
                return;
            }
            case THEATRE_OF_BLOOD_REWARD_GROUP_ID: {
                if (killType != KillType.TOB && killType != KillType.TOB_SM && killType != KillType.TOB_HM) {
                    return;
                }

                switch (killType) {
                    case TOB:
                        fileName = "Theatre of Blood(" + killCountNumber + ")";
                        break;
                    case TOB_SM:
                        fileName = "Theatre of Blood Story Mode(" + killCountNumber + ")";
                        break;
                    case TOB_HM:
                        fileName = "Theatre of Blood Hard Mode(" + killCountNumber + ")";
                        break;
                    default:
                        throw new IllegalStateException();
                }

                clipSubDir = SD_BOSS_KILLS;
                killType = null;
                killCountNumber = 0;
                break;
            }
            case TOA_REWARD_GROUP_ID: {
                if (killType != KillType.TOA && killType != KillType.TOA_ENTRY_MODE && killType != KillType.TOA_EXPERT_MODE) {
                    return;
                }

                switch (killType) {
                    case TOA:
                        fileName = "Tombs of Amascut(" + killCountNumber + ")";
                        break;
                    case TOA_ENTRY_MODE:
                        fileName = "Tombs of Amascut Entry Mode(" + killCountNumber + ")";
                        break;
                    case TOA_EXPERT_MODE:
                        fileName = "Tombs of Amascut Expert Mode(" + killCountNumber + ")";
                        break;
                    default:
                        throw new IllegalStateException();
                }

                clipSubDir = SD_BOSS_KILLS;
                killType = null;
                killCountNumber = 0;
                break;
            }
            case BARROWS_REWARD_GROUP_ID: {
                if (killType != KillType.BARROWS) {
                    return;
                }

                fileName = "Barrows(" + killCountNumber + ")";
                clipSubDir = SD_BOSS_KILLS;
                killType = null;
                killCountNumber = 0;
                break;
            }
            case LEVEL_UP_GROUP_ID:
            case DIALOG_SPRITE_GROUP_ID:
            case QUEST_COMPLETED_GROUP_ID: {
                // level up widget gets loaded prior to the text being set, so wait until the next tick
                shouldTakeClip = true;
                return;
            }
            case CLUE_SCROLL_REWARD_GROUP_ID: {
                if (clueType == null || clueNumber == null) {
                    return;
                }

                fileName = Character.toUpperCase(clueType.charAt(0)) + clueType.substring(1) + "(" + clueNumber + ")";
                clipSubDir = SD_CLUE_SCROLL_REWARDS;
                clueType = null;
                clueNumber = null;
                break;
            }
            default:
                return;
        }

        startReplayBufferSave(fileName, clipSubDir);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired scriptPreFired) {
        switch (scriptPreFired.getScriptId()) {
            case ScriptID.NOTIFICATION_START:
                notificationStarted = true;
                break;
            case ScriptID.NOTIFICATION_DELAY:
                if (!notificationStarted) {
                    return;
                }
                String topText = client.getVarcStrValue(VarClientStr.NOTIFICATION_TOP_TEXT);
                String bottomText = client.getVarcStrValue(VarClientStr.NOTIFICATION_BOTTOM_TEXT);
                if (topText.equalsIgnoreCase("Collection log") && config.clipCollectionLogEntries()) {
                    String entry = Text.removeTags(bottomText).substring("New item:".length());
                    String fileName = "Collection log (" + entry + ")";
                    startReplayBufferSave(fileName, SD_COLLECTION_LOG);
                }
                if (topText.equalsIgnoreCase("Combat Task Completed!") && config.clipCombatAchievements() && client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENTS_POPUP) == 0) {
                    String entry = Text.removeTags(bottomText).substring("Task Completed: ".length());
                    String fileName = "Combat task (" + entry.replaceAll("[:?]", "") + ")";
                    startReplayBufferSave(fileName, SD_COMBAT_ACHIEVEMENTS);
                }
                notificationStarted = false;
                break;
        }
    }

    private void manualScreenshot() {
        startReplayBufferSave("Manual", "Manual");
    }

    /**
     * Receives a WidgetInfo pointing to the middle widget of the level-up dialog,
     * and parses it into a shortened string for filename usage.
     *
     * @param levelUpLevel WidgetInfo pointing to the required text widget,
     *                     with the format "Your Skill (level is/are) now 99."
     * @return Shortened string in the format "Skill(99)"
     */
    String parseLevelUpWidget(WidgetInfo levelUpLevel) {
        Widget levelChild = client.getWidget(levelUpLevel);
        if (levelChild == null) {
            return null;
        }

        Matcher m = LEVEL_UP_PATTERN.matcher(levelChild.getText());
        if (!m.matches()) {
            return null;
        }

        String skillName = m.group(1);
        String skillLevel = m.group(2);
        return skillName + "(" + skillLevel + ")";
    }

    /**
     * Parses the passed quest completion dialog text into a shortened string for filename usage.
     *
     * @param text The {@link Widget#getText() text} of the {@link WidgetInfo#QUEST_COMPLETED_NAME_TEXT} widget.
     * @return Shortened string in the format "Quest(The Corsair Curse)"
     */
    @VisibleForTesting
    static String parseQuestCompletedWidget(final String text) {
        // "You have completed The Corsair Curse!"
        final Matcher questMatch1 = QUEST_PATTERN_1.matcher(text);
        // "'One Small Favour' completed!"
        final Matcher questMatch2 = QUEST_PATTERN_2.matcher(text);
        final Matcher questMatchFinal = questMatch1.matches() ? questMatch1 : questMatch2;
        if (!questMatchFinal.matches()) {
            return "Quest(quest not found)";
        }

        String quest = questMatchFinal.group("quest");
        String verb = questMatchFinal.group("verb") != null ? questMatchFinal.group("verb") : "";

        if (verb.contains("kind of")) {
            quest += " partial completion";
        } else if (verb.contains("completely")) {
            quest += " II";
        }

        if (RFD_TAGS.stream().anyMatch((quest + verb)::contains)) {
            quest = "Recipe for Disaster - " + quest;
        }

        if (WORD_QUEST_IN_NAME_TAGS.stream().anyMatch(quest::contains)) {
            quest += " Quest";
        }

        return "Quest(" + quest + ')';
    }

    /**
     * Parses the Barbarian Assault high gamble reward dialog text into a shortened string for filename usage.
     *
     * @param text The {@link Widget#getText() text} of the {@link WidgetInfo#DIALOG_SPRITE_TEXT} widget.
     * @return Shortened string in the format "High Gamble(100)"
     */
    @VisibleForTesting
    static String parseBAHighGambleWidget(final String text) {
        final Matcher highGambleMatch = BA_HIGH_GAMBLE_REWARD_PATTERN.matcher(text);
        if (highGambleMatch.find()) {
            String gambleCount = highGambleMatch.group("gambleCount");
            return String.format("High Gamble(%s)", gambleCount);
        }

        return "High Gamble(count not found)";
    }

    /**
     * Parses a combat achievement success chat message into a filename-safe string.
     *
     * @param text A received chat message which may or may not be from completing a combat achievement.
     * @return A formatted string of the achieved combat task name, or the empty string if the passed message
     * is not a combat achievement completion message.
     */
    @VisibleForTesting
    static String parseCombatAchievementWidget(final String text) {
        final Matcher m = COMBAT_ACHIEVEMENTS_PATTERN.matcher(text);
        if (m.matches()) {
            String task = m.group("task").replaceAll("[:?]", "");
            return "Combat task (" + task + ")";
        }
        return "";
    }

    private void onOBSWSReady() {
        // OBS WebSocket is ready

        // Let's fetch Replay Buffer Settings and save the data we need from it
        this.obsController.getOutputSettings("Replay Buffer", getOutputSettingsResponse -> {
            this.baseObsOutputSettings = getOutputSettingsResponse.getOutputSettings();
            if (this.baseObsOutputSettings.has("max_time_sec")) {
                this.replayBufferDuration = this.baseObsOutputSettings.get("max_time_sec").getAsLong();
            }
        });
    }

    private synchronized void setReplayBufferOutput(String fileName, String subDir) {
        JsonObject alteredOutputSettings = this.baseObsOutputSettings.deepCopy();
        alteredOutputSettings.addProperty("path", this.baseObsOutputSettings.get("path").getAsString() + "/" + subDir);
        alteredOutputSettings.addProperty("directory", this.baseObsOutputSettings.get("directory").getAsString() + "/" + subDir);
        alteredOutputSettings.addProperty("format", fileName + " " + this.OBS_BASE_FORMAT);

        this.obsController.setOutputSettings("Replay Buffer", alteredOutputSettings, 1000);
    }

    private synchronized void resetReplayBufferOutput() {
        JsonObject alteredOutputSettings = this.baseObsOutputSettings.deepCopy();

        this.obsController.setOutputSettings("Replay Buffer", alteredOutputSettings, 1000);
    }

    private synchronized void sendSaveReplayBufferRequest() {
        this.obsController.saveReplayBuffer(saveReplayBufferResponse -> {

            if (!saveReplayBufferResponse.isSuccessful()) {
                if (this.config.notifyWhenClipTaken()) {
                    this.notifier.notify("OBS Auto-clip save failed");
                }
            }
        });
    }

    private void onSuccessfulSave(ReplayBufferSavedEvent event) {
        final StringBuilder notificationStringBuilder = new StringBuilder();
        if (this.config.notifyWhenClipTaken()) {
            notificationStringBuilder
                    .append("OBS Auto-clip save successful ")
                    .append("(path: ")
                    .append(event.getSavedReplayPath())
            ;
            if (this.replayBufferDuration > 0) {
                notificationStringBuilder
                        .append(", duration: ")
                        .append(replayBufferDuration)
                        .append("s");
            }
            notificationStringBuilder.append(").");

            this.notifier.notify(notificationStringBuilder.toString());
        }
        this.executorService.submit(this::resetReplayBufferOutput);
    }

    /**
     * Start Replay Buffer Save
     */
    private synchronized void startReplayBufferSave(String fileName, String subDir) {
        try {
            this.executor.schedule(() -> {
                this.executorService.submit(() -> this.setReplayBufferOutput(fileName, subDir));
                this.executorService.schedule(this::sendSaveReplayBufferRequest, 1, TimeUnit.SECONDS);
            }, config.obsDelay(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("startReplayBufferSave error 2", e);
        }
    }

    private boolean isInsideGauntlet() {
        return this.client.isInInstancedRegion()
                && this.client.getMapRegions().length > 0
                && (this.client.getMapRegions()[0] == GAUNTLET_REGION
                || this.client.getMapRegions()[0] == CORRUPTED_GAUNTLET_REGION);
    }

    @VisibleForTesting
    int getClueNumber() {
        return clueNumber;
    }

    @VisibleForTesting
    String getClueType() {
        return clueType;
    }

    @VisibleForTesting
    KillType getKillType() {
        return killType;
    }

    @VisibleForTesting
    int getKillCountNumber() {
        return killCountNumber;
    }
}