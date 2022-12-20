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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.util.ImageUploadStyle;

@ConfigGroup("clip")
public interface AutoClipConfig extends Config
{
	@ConfigSection(
			name = "What to Record",
			description = "All the options that select what to clip",
			position = 99
	)
	String whatSection = "what";

	@ConfigSection(
			name = "OBS Settings",
			description = "All the required options for OBS",
			position = 1
	)
	String obsSection = "OBS Settings";

	@ConfigItem(
			keyName = "notifyWhenClipTaken",
			name = "Notify When Taken",
			description = "Configures whether or not you are notified when a clip has been taken",
			position = 2
	)
	default boolean notifyWhenClipTaken()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipHotkey",
			name = "Record hotkey",
			description = "When you press this key a clip will be taken",
			position = 4
	)
	default Keybind clipHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "clipRewards",
			name = "Record Rewards",
			description = "Configures whether clips are taken of clues, barrows, and quest completion",
			position = 3,
			section = whatSection
	)
	default boolean clipRewards()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipLevels",
			name = "Record Levels",
			description = "Configures whether clips are taken of level ups",
			position = 4,
			section = whatSection
	)
	default boolean clipLevels()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipKingdom",
			name = "Record Kingdom Reward",
			description = "Configures whether clips are taken of Kingdom Reward",
			position = 5,
			section = whatSection
	)
	default boolean clipKingdom()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipPets",
			name = "Record Pet",
			description = "Configures whether clips are taken of receiving pets",
			position = 6,
			section = whatSection
	)
	default boolean clipPet()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipKills",
			name = "Record PvP Kills",
			description = "Configures whether or not clips are automatically taken of PvP kills",
			position = 8,
			section = whatSection
	)
	default boolean clipKills()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipBoss",
			name = "Record Boss Kills",
			description = "Configures whether or not clips are automatically taken of boss kills",
			position = 9,
			section = whatSection
	)
	default boolean clipBossKills()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipPlayerDeath",
			name = "Record Deaths",
			description = "Configures whether or not clips are automatically taken when you die.",
			position = 10,
			section = whatSection
	)
	default boolean clipPlayerDeath()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipFriendDeath",
			name = "Record Friend Deaths",
			description = "Configures whether or not clips are automatically taken when friends or friends chat members die.",
			position = 11,
			section = whatSection
	)
	default boolean clipFriendDeath()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipClanDeath",
			name = "Record Clan Deaths",
			description = "Configures whether or not clips are automatically taken when clan members die.",
			position = 12,
			section = whatSection
	)
	default boolean clipClanDeath()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipDuels",
			name = "Record Duels",
			description = "Configures whether or not clips are automatically taken of the duel end screen.",
			position = 13,
			section = whatSection
	)
	default boolean clipDuels()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipValuableDrop",
			name = "Record Valuable drops",
			description = "Configures whether or not clips are automatically taken when you receive a valuable drop.",
			position = 14,
			section = whatSection
	)
	default boolean clipValuableDrop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipValuableDropThreshold",
			name = "Valuable Threshold",
			description = "The minimum value to save clips of valuable drops.",
			position = 15,
			section = whatSection
	)
	default int valuableDropThreshold()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "clipUntradeableDrop",
			name = "Record Untradeable drops",
			description = "Configures whether or not clips are automatically taken when you receive an untradeable drop.",
			position = 16,
			section = whatSection
	)
	default boolean clipUntradeableDrop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipBaHighGamble",
			name = "Record BA high gambles",
			description = "Take a clip of your reward from a high gamble at Barbarian Assault.",
			position = 18,
			section = whatSection
	)
	default boolean clipHighGamble()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clipCollectionLogEntries",
			name = "Record collection log entries",
			description = "Take a clip when completing an entry in the collection log",
			position = 19,
			section = whatSection
	)
	default boolean clipCollectionLogEntries()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clipCombatAchievements",
			name = "Record combat achievements",
			description = "Take a clip when completing a combat achievement task",
			position = 20,
			section = whatSection
	)
	default boolean clipCombatAchievements()
	{
		return true;
	}

	@ConfigItem(
			keyName = "obsServerHost",
			name = "Websocket host address",
			description = "The host address on which OBS Websocket is bound",
			position = 1,
			section = obsSection
	)
	default String obsServerHost()
	{
		return "127.0.0.1";
	}
	@ConfigItem(
			keyName = "obsServerPort",
			name = "Websocket port",
			description = "The port on which OBS Websocket runs",
			position = 3,
			section = obsSection
	)
	default int obsServerPort()
	{
		return 4455;
	}

	@ConfigItem(
			keyName = "obsServerPassword",
			name = "Websocket password",
			description = "The password to authenticate with the server (optional)",
			position = 4,
			section = obsSection
	)
	default String obsServerPassword()
	{
		return "";
	}

	@ConfigItem(
			keyName = "obsDelay",
			name = "Delay (seconds)",
			description = "How many seconds to wait until the replay buffer will save (0 = instant)",
			position = 4,
			section = obsSection
	)
	default int obsDelay()
	{
		return 0;
	}
}