package com.dynxsty.dsbmobilebot.config;

import com.dynxsty.dsbmobilebot.config.exception.UnknownPropertyException;
import com.dynxsty.dsbmobilebot.config.guild.TimeTableConfig;
import com.dynxsty.dsbmobilebot.config.guild.SlashCommandConfig;
import com.dynxsty.dsbmobilebot.util.ReflectionUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A collection of guild-specific configuration items, each of which represents
 * a group of many individual settings.
 */
@Data
@Slf4j
public class GuildConfig {
	private transient Guild guild;
	private transient Path file;

	private TimeTableConfig plan;
	private SlashCommandConfig slashCommand;

	public GuildConfig(Guild guild, Path file) {
		this.file = file;
		// Initialize all config items.
		this.plan = new TimeTableConfig();
		this.slashCommand = new SlashCommandConfig();
		this.setGuild(guild);
	}

	/**
	 * Loads an instance of the configuration from the given path, or creates a
	 * new empty configuration file there if none exists yet.
	 *
	 * @param guild The guild to load config for.
	 * @param file  The path to the configuration JSON file.
	 * @return The config that was loaded.
	 * @throws JsonSyntaxException  if the config file's JSON is invalid.
	 * @throws UncheckedIOException if an IO error occurs.
	 */
	public static GuildConfig loadOrCreate(Guild guild, Path file) {
		Gson gson = new GsonBuilder().create();
		GuildConfig config;
		if (Files.exists(file)) {
			try (var reader = Files.newBufferedReader(file)) {
				config = gson.fromJson(reader, GuildConfig.class);
				config.setFile(file);
				config.setGuild(guild);
				log.info("Loaded config from {}", file);
			} catch (JsonSyntaxException e) {
				log.error("Invalid JSON found! Please fix or remove config file " + file + " and restart.", e);
				throw e;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			log.info("No config file found. Creating an empty one at {}", file);
			config = new GuildConfig(guild, file);
			config.flush();
		}

		return config;
	}

	private void setGuild(Guild guild) {
		this.guild = guild;
		if (this.plan == null) this.plan = new TimeTableConfig();
		this.plan.setGuildConfig(this);
		if (this.slashCommand == null) this.slashCommand = new SlashCommandConfig();
		this.slashCommand.setGuildConfig(this);
	}

	/**
	 * Saves this config to its file path.
	 */
	public synchronized void flush() {
		Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
		try (var writer = Files.newBufferedWriter(this.file)) {
			gson.toJson(this, writer);
			writer.flush();
		} catch (IOException e) {
			log.error("Could not flush config.", e);
		}
	}

	/**
	 * Attempts to resolve a configuration property value by its name, using a
	 * '.' to concatenate property names. For example, the {@link TimeTableConfig} has
	 * a property called <code>logChannelId</code>. We can resolve it via the
	 * full name <code>moderation.logChannelId</code>, using the <code>moderation</code> field
	 * of {@link GuildConfig} followed by the <code>logChannelId</code> field from
	 * {@link TimeTableConfig}.
	 *
	 * @param propertyName The name of the property.
	 * @return The value of the property, if found, or null otherwise.
	 */
	@Nullable
	public Object resolve(String propertyName) throws UnknownPropertyException {
		var result = ReflectionUtils.resolveField(propertyName, this);
		return result.map(pair -> {
			try {
				return pair.first().get(pair.second());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				return null;
			}
		}).orElse(null);
	}

	/**
	 * Attempts to set a configuration property's value by its name, using '.'
	 * to concatenate property names, similar to {@link GuildConfig#resolve(String)}.
	 *
	 * @param propertyName The name of the property to set.
	 * @param value        The value to set.
	 */
	public void set(String propertyName, String value) throws UnknownPropertyException {
		var result = ReflectionUtils.resolveField(propertyName, this);
		result.ifPresent(pair -> {
			try {
				ReflectionUtils.set(pair.first(), pair.second(), value);
				this.flush();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		});
	}
}
