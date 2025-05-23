package ch.njol.skript.aliases;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.config.Config;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.entity.EntityData;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.localization.*;
import ch.njol.skript.log.BlockingLogHandler;
import ch.njol.skript.util.EnchantmentType;
import ch.njol.skript.util.Utils;
import ch.njol.skript.util.Version;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.script.Script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Aliases {
	static final boolean USING_ITEM_COMPONENTS = Skript.isRunningMinecraft(1, 20, 5);

	private static final AliasesProvider provider = createProvider(10000, null);
	private static final AliasesParser parser = createParser(provider);
	// this is not an alias!
	private final static ItemType everything = new ItemType();
	private final static Message m_empty_string = new Message("aliases.empty string");
	private final static ArgsMessage m_invalid_item_type = new ArgsMessage("aliases.invalid item type");
	private final static Message m_outside_section = new Message("aliases.outside section");
	private final static RegexMessage p_any = new RegexMessage("aliases.any", "", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_every = new RegexMessage("aliases.every", "", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_of_every = new RegexMessage("aliases.of every", "(\\d+) ", " (.+)", Pattern.CASE_INSENSITIVE);
	private final static RegexMessage p_of = new RegexMessage("aliases.of", "(\\d+) (?:", " )?(.+)", Pattern.CASE_INSENSITIVE);

	/**
	 * Go through these whenever aliases are reloaded, and update them.
	 */
	private static final Map<String, ItemType> trackedTypes = new HashMap<>();

	/**
	 * If user had an obscure config option set, don't crash due to missing
	 * Java item types.
	 */
	private static final boolean noHardExceptions = SkriptConfig.apiSoftExceptions.value();
	static String itemSingular = "item";
	static String itemPlural = "items";
	static String blockSingular = "block";
	static String blockPlural = "blocks";

	static {
		everything.setAll(true);
		ItemData all = new ItemData(Material.AIR);
		all.isAnything = true;
		everything.add(all);
	}

	@Nullable
	private static ItemType getAlias_i(final String s) {
		// Check script aliases first
		ScriptAliases aliases = getScriptAliases();
		if (aliases != null) {
			return aliases.provider.getAlias(s); // Delegates to global provider if needed
		}

		return provider.getAlias(s);
	}

	/**
	 * Creates an aliases provider with Skript's default configuration.
	 * @param expectedCount Expected alias count.
	 * @param parent Parent aliases provider.
	 * @return Aliases provider.
	 */
	private static AliasesProvider createProvider(int expectedCount, @Nullable AliasesProvider parent) {
		return new AliasesProvider(expectedCount, parent);
	}

	/**
	 * Creates an aliases parser with Skript's default configuration.
	 * @return Aliases parser.
	 */
	private static AliasesParser createParser(AliasesProvider provider) {
		AliasesParser parser = new AliasesParser(provider);

		// Register standard conditions
		parser.registerCondition("minecraft version", (str) -> {
			int orNewer = str.indexOf("or newer"); // For example: 1.12 or newer
			if (orNewer != -1) {
				@SuppressWarnings("null")
				Version ver = new Version(str.substring(0, orNewer - 1));
				return Skript.getMinecraftVersion().compareTo(ver) >= 0;
			}

			int orOlder = str.indexOf("or older"); // For example: 1.11 or older
			if (orOlder != -1) {
				@SuppressWarnings("null")
				Version ver = new Version(str.substring(0, orOlder - 1));
				return Skript.getMinecraftVersion().compareTo(ver) <= 0;
			}

			int to = str.indexOf("to"); // For example: 1.11 to 1.12
			if (to != -1) {
				@SuppressWarnings("null")
				Version first = new Version(str.substring(0, to - 1));
				@SuppressWarnings("null")
				Version second = new Version(str.substring(to + 3));
				Version current = Skript.getMinecraftVersion();
				return current.compareTo(first) >= 0 && current.compareTo(second) <= 0;
			}

			return Skript.getMinecraftVersion().equals(new Version(str));
		});

		return parser;
	}

	/**
	 * Concatenates parts of an alias's name. This currently 'lowercases' the first character of any part if there's no space in front of it. It also replaces double spaces with a
	 * single one and trims the resulting string.
	 *
	 * @param parts
	 */
	static String concatenate(final String... parts) {
		assert parts.length >= 2;
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].isEmpty())
				continue;
			if (b.length() == 0) {
				b.append(parts[i]);
				continue;
			}
			final char c = parts[i].charAt(0);
			if (Character.isUpperCase(c) && b.charAt(b.length() - 1) != ' ') {
				b.append(Character.toLowerCase(c) + parts[i].substring(1));
			} else {
				b.append(parts[i]);
			}
		}
		return "" + b.toString().replace("  ", " ").trim();
	}

	@Nullable
	private static MaterialName getMaterialNameData(ItemData type) {
		// Check script aliases first
		ScriptAliases aliases = getScriptAliases();
		if (aliases != null) {
			return aliases.provider.getMaterialName(type);
		}

		// Then global aliases
		return provider.getMaterialName(type);
	}

	public static String getMaterialName(ItemData type, boolean plural) {
		MaterialName name = getMaterialNameData(type);
		if (name == null) {
			return "" + type.type;
		}
		return name.toString(plural);
	}

	/**
	 * @return The item's gender or -1 if no name is found
	 */
	public static int getGender(ItemData item) {
		MaterialName n = getMaterialNameData(item);
		if (n != null)
			return n.gender;
		return -1;
	}

	/**
	 * Parses an ItemType to be used as an alias, i.e. it doesn't parse 'all'/'every' and the amount.
	 *
	 * @param s mixed case string
	 * @return A new ItemType representing the given value
	 */
	@Nullable
	public static ItemType parseAlias(final String s) {
		if (s.isEmpty()) {
			Skript.error(m_empty_string.toString());
			return null;
		}
		if (s.equals("*"))
			return everything;

		final ItemType t = new ItemType();

		final String[] types = s.split("\\s*,\\s*");
		for (final String type : types) {
			if (type == null || parseType(type, t, true) == null)
				return null;
		}

		return t;
	}

	/**
	 * Parses an ItemType.
	 * <p>
	 * Prints errors.
	 *
	 * @param s
	 * @return The parsed ItemType or null if the input is invalid.
	 */
	@Nullable
	public static ItemType parseItemType(String s) {
		if (s.isEmpty())
			return null;
		s = s.trim();

		final ItemType t = new ItemType();

		Matcher m;
		if ((m = p_of_every.matcher(s)).matches()) {
			t.setAmount(Utils.parseInt("" + m.group(1)));
			t.setAll(true);
			s = "" + m.group(m.groupCount());
		} else if ((m = p_of.matcher(s)).matches()) {
			t.setAmount(Utils.parseInt("" + m.group(1)));
			s = "" + m.group(m.groupCount());
		} else if ((m = p_every.matcher(s)).matches()) {
			t.setAll(true);
			s = "" + m.group(m.groupCount());
		} else {
			final int l = s.length();
			s = Noun.stripIndefiniteArticle(s);
			if (s.length() != l) // had indefinite article
				t.setAmount(1);
		}

		String lc = s.toLowerCase(Locale.ENGLISH);
		String of = Language.getSpaced("of").toLowerCase();
		int c = -1;
		outer:
		while ((c = lc.indexOf(of, c + 1)) != -1) {
			ItemType t2 = t.clone();
			try (BlockingLogHandler ignored = new BlockingLogHandler().start()) {
				if (parseType("" + s.substring(0, c), t2, false) == null)
					continue;
			}
			if (t2.numTypes() == 0)
				continue;
			String[] enchs = lc.substring(c + of.length()).split("\\s*(,|" + Pattern.quote(Language.get("and")) + ")\\s*");
			for (final String ench : enchs) {
				EnchantmentType e = EnchantmentType.parse("" + ench);
				if (e == null)
					continue outer;
				t2.addEnchantments(e);
			}
			return t2;
		}

		if (parseType(s, t, false) == null)
			return null;

		if (t.numTypes() == 0)
			return null;

		return t;
	}

	/**
	 * Prints errors.
	 *
	 * @param s The string holding the type, can be either a number or an alias, plus an optional data part. Case does not matter.
	 * @param t The ItemType to add the parsed ItemData(s) to (i.e. this ItemType will be modified)
	 * @param isAlias Whether this type is parsed for an alias.
	 * @return The given item type or null if the input couldn't be parsed.
	 */
	@Nullable
	private static ItemType parseType(final String s, final ItemType t, final boolean isAlias) {
		ItemType i;
		if (s.isEmpty()) {
			t.add(new ItemData(Material.AIR));
			return t;
		} else if (s.matches("\\d+")) {
			return null;
		} else if ((i = getAlias(s)) != null) {
			for (ItemData d : i) {
				t.add(d.clone());
			}
			return t;
		}
		if (isAlias)
			Skript.error(m_invalid_item_type.toString(s));
		return null;
	}

	/**
	 * Gets an alias from the aliases defined in the config.
	 *
	 * @param rawInput The alias to get, case does not matter
	 * @return A copy of the ItemType represented by the given alias or null if no such alias exists.
	 */
	@Nullable
	private static ItemType getAlias(final String rawInput) {
		String input = rawInput.toLowerCase(Locale.ENGLISH).trim();
		ItemType itemType = getAlias_i(input);
		if (itemType != null)
			return itemType.clone();

		// Try to parse as Minecraft key `minecraft:some_item` or `some_item`
		if ((input.contains(":") || input.contains("_")) && !input.contains(" ")) {
			NamespacedKey namespacedKey = NamespacedKey.fromString(input);
			if (namespacedKey != null) {
				Material material = Registry.MATERIAL.get(namespacedKey);
				if (material != null)
					return new ItemType(material);
			}
		}
		// try to parse `ACTUALNAME block` as ACTUALNAME
		if (input.endsWith(" " + blockSingular) || input.endsWith(" " + blockPlural)) {
			String stripped = input.substring(0, input.lastIndexOf(" "));
			itemType = getAlias_i(stripped);
			if (itemType != null) {
				itemType = itemType.clone();
				// remove all non-block datas and types that already end with "block"
				for (int j = 0; j < itemType.numTypes(); j++) {
					ItemData d = itemType.getTypes().get(j);
					if (!d.getType().isBlock() || d.getType().getKey().getKey().endsWith(blockSingular)) {
						itemType.remove(d);
						j--;
					}
				}
				// if no block itemdatas were found, return null
				if (itemType.getTypes().isEmpty())
					return null;
				return itemType;
			}
		// do the same for items
		} else if (input.endsWith(" " + itemSingular) || input.endsWith(" " + itemPlural)) {
			String stripped = input.substring(0, input.lastIndexOf(" "));
			itemType = getAlias_i(stripped);
			if (itemType != null) {
				itemType = itemType.clone();
				// remove all non-item datas
				for (int j = 0; j < itemType.numTypes(); j++) {
					ItemData data = itemType.getTypes().get(j);
					if (!data.isAnything && !data.getType().isItem()) {
						itemType.remove(data);
						--j;
					}
				}
				// if no item itemdatas were found, return null
				if (itemType.getTypes().isEmpty())
					return null;
				return itemType;
			}
		}
		return null;
	}

	/**
	 * Clears aliases. Make sure to load them after this!
	 */
	public static void clear() {
		provider.clearAliases();
	}

	/**
	 * Loads aliases from Skript's standard locations.
	 * Exceptions will be logged, but not thrown.
	 *
	 * @deprecated Freezes server on call. Use {@link #loadAsync()} instead.
	 */
	@Deprecated(since = "2.10.0", forRemoval = true)
	public static void load() {
		try {
			long start = System.currentTimeMillis();
			loadInternal();
			Skript.info("Loaded " + provider.getAliasCount() + " aliases in " + (System.currentTimeMillis() - start) + "ms");
		} catch (IOException e) {
			Skript.exception(e);
		}
	}

	/**
	 * Loads aliases from Skript's standard locations asynchronously.
	 * Exceptions will be logged, but not thrown.
	 *
	 * @return A future that completes when the aliases are loaded.
	 * The returned value is true if the loading was successful, false otherwise.
	 */
	public static CompletableFuture<Boolean> loadAsync() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				long start = System.currentTimeMillis();
				loadInternal();
				Skript.info("Loaded " + provider.getAliasCount() + " aliases in " + (System.currentTimeMillis() - start) + "ms");
				return true;
			} catch (StackOverflowError e) {
				/*
				 * Returns true if the underlying installed Java/JVM is 32-bit, false otherwise.
				 * Note that this depends on a internal system property and these can always be overridden by user using -D JVM options,
				 * more specifically, this method will return false on non OracleJDK/OpenJDK based JVMs, that don't include bit information in java.vm.name system property
				 */
				if (System.getProperty("java.vm.name").contains("32")) {
					Skript.error("");
					Skript.error("There was a StackOverflowError that occurred while loading aliases.");
					Skript.error("As you are currently using 32-bit Java, please update to 64-bit Java to resolve the error.");
					Skript.error("Please report this issue to our GitHub only if updating to 64-bit Java does not fix the issue.");
					Skript.error("");
				} else {
					Skript.exception(e);
					Bukkit.getPluginManager().disablePlugin(Skript.getInstance());
				}
				return false;
			} catch (IOException e) {
				Skript.exception(e);
				return false;
			}
		});
	}

	/**
	 * Temporarily create an alias for materials which do not have aliases yet.
	 */
	private static void loadMissingAliases() {
		if (!Skript.methodExists(Material.class, "getKey"))
			return;
		boolean modItemRegistered = false;
		for (Material material : Material.values()) {
			if (!material.isLegacy() && !provider.hasAliasForMaterial(material)) {
				NamespacedKey key = material.getKey();
				// mod:an_item -> (mod's an item) | (an item from mod)
				// minecraft:dirt -> dirt
				if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
					parser.loadAlias(key.getKey().replace("_", " ") + "¦s", key.toString());
				} else {
					if (!modItemRegistered) modItemRegistered = true;
					parser.loadAlias((key.getNamespace() + "'s " + key.getKey() + "¦s").replace("_", " "), key.toString());
					parser.loadAlias((key.getKey() + "¦s from " + key.getNamespace()).replace("_", " "), key.toString());
				}
				Skript.debug(ChatColor.YELLOW + "Creating temporary alias for: " + key);
			}
		}

		if (modItemRegistered) {
			Skript.warning("==============================================================");
			Skript.warning("Some materials were found that seem to be modded.");
			Skript.warning("An item that has the id 'mod:item' can be used as 'mod's item' or 'item from mod'.");
			Skript.warning("WARNING: Skript does not officially support any modded servers.");
			Skript.warning("Any issues you encounter related to modded items will be your responsibility to fix.");
			Skript.warning("==============================================================");
		}
	}

	private static void loadInternal() throws IOException {
		Path dataFolder = Skript.getInstance().getDataFolder().toPath();

		// Load aliases.zip OR aliases from jar (never both)
		Path zipPath = dataFolder.resolve("aliases-english.zip");
		if (!SkriptConfig.loadDefaultAliases.value()) {
			// Or do nothing, if user requested that default aliases are not loaded
		} else if (Files.exists(zipPath)) { // Load if it exists
			try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, Skript.class.getClassLoader())) {
				assert zipFs != null; // It better not be...
				Path aliasesPath = zipFs.getPath("/");
				assert aliasesPath != null;
				loadDirectory(aliasesPath);
			}
		} else { // Fall back to jar loading
			try {
				URI jarUri = Skript.class.getProtectionDomain().getCodeSource().getLocation().toURI();
				try (FileSystem zipFs = FileSystems.newFileSystem(Paths.get(jarUri), Skript.class.getClassLoader())) {
					assert zipFs != null;
					Path aliasesPath = zipFs.getPath("/", "aliases-english");
					assert aliasesPath != null;
					loadDirectory(aliasesPath);
				}
			} catch (URISyntaxException e) {
				assert false;
			}

		}

		// Load everything from aliases folder (user aliases)
		Path aliasesFolder = dataFolder.resolve("aliases");
		if (Files.exists(aliasesFolder)) {
			assert aliasesFolder != null;
			loadDirectory(aliasesFolder);
		}

		// generate aliases from item names for any missing items
		loadMissingAliases();

		// Update tracked item types
		for (Map.Entry<String, ItemType> entry : trackedTypes.entrySet()) {
			@SuppressWarnings("null") // No null keys in this map
			ItemType type = parseItemType(entry.getKey());
			if (type == null)
				Skript.warning("Alias '" + entry.getKey() + "' is required by Skript, but does not exist anymore. "
								   + "Make sure to fix this before restarting the server.");
			else
				entry.getValue().setTo(type);
		}
	}

	/**
	 * Loads aliases from given directory.
	 * @param dir Directory of aliases.
	 * @throws IOException If something goes wrong with loading.
	 */
	public static void loadDirectory(Path dir) throws IOException {
		try {
			Files.list(dir).sorted().forEach((f) -> {
				assert f != null;
				try {
					String name = f.getFileName().toString();
					if (Files.isDirectory(f) && !name.startsWith("."))
						loadDirectory(f);
					else if (name.endsWith(".sk"))
						load(f);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Loads aliases from given path.
	 * @param f Path of alias file.
	 * @throws IOException If something goes wrong with loading.
	 */
	public static void load(Path f) throws IOException {
		Config config = new Config(f, false, false, "=");
		load(config);
	}

	/**
	 * Loads aliases from configuration.
	 * @param config Configuration containing the aliases.
	 */
	public static void load(Config config) {
		for (Node n : config.getMainNode()) {
			if (!(n instanceof SectionNode)) {
				Skript.error(m_outside_section.toString());
				continue;
			}

			parser.load((SectionNode) n);
		}
	}

	/**
	 * Gets a Vanilla Minecraft material id for given item data.
	 * @param data Item data.
	 * @return Minecraft item id or null.
	 */
	@Nullable
	public static String getMinecraftId(ItemData data) {
		ScriptAliases aliases = getScriptAliases();
		if (aliases != null) {
			return aliases.provider.getMinecraftId(data);
		}
		return provider.getMinecraftId(data);
	}

	/**
	 * Gets an entity type related to given item. For example, an armor stand
	 * item is related with armor stand entity.
	 * @param data Item data.
	 * @return Entity type or null.
	 */
	@Nullable
	public static EntityData<?> getRelatedEntity(ItemData data) {
		ScriptAliases aliases = getScriptAliases();
		if (aliases != null) {
			return aliases.provider.getRelatedEntity(data);
		}
		return provider.getRelatedEntity(data);
	}

	/**
	 * Gets an item type that matches the given name.
	 * If it doesn't exist, an exception is thrown instead.
	 *
	 * <p>Item types provided by this method are updated when aliases are
	 * reloaded. However, this also means they are tracked by aliases system
	 * and NOT necessarily garbage-collected.
	 *
	 * <p>Relying on this method to create item types is not safe,
	 * as users can change aliases at any point. ItemTypes should instead be created
	 * via {@link Material}s, {@link org.bukkit.Tag}s, or any other manual method.
	 *
	 * @param name Name of item to search from aliases.
	 * @return An item.
	 * @throws IllegalArgumentException When item is not found.
	 */
	@Deprecated(since = "2.9.0", forRemoval = true)
	public static ItemType javaItemType(String name) {
		ItemType type = parseItemType(name);
		if (type == null) {
			if (noHardExceptions) {
				Skript.error("type " + name + " not found");
				type = new ItemType(); // Return garbage
			} else {
				throw new IllegalArgumentException("type " + name + " not found");
			}
		}
		trackedTypes.put(name, type);
		return type;
	}

	/**
	 * Creates an aliases provider to be used by given addon. It can be used to
	 * register aliases and variations to be used in scripts.
	 * @param addon Skript addon.
	 * @return Aliases provider.
	 */
	public static AliasesProvider getAddonProvider(@Nullable SkriptAddon addon) {
		if (addon == null) {
			throw new IllegalArgumentException("addon needed");
		}

		// TODO in future, maybe record and allow unloading addon-provided aliases?
		return provider; // For now, just allow loading aliases easily
	}

	/**
	 * Creates script aliases for the provided Script.
	 * @return Script aliases that are ready to be added to.
	 */
	public static ScriptAliases createScriptAliases(Script script) {
		AliasesProvider localProvider = createProvider(10, provider);
		ScriptAliases aliases = new ScriptAliases(localProvider, createParser(localProvider));
		script.addData(aliases);
		return aliases;
	}

	/**
	 * Internal method for obtaining ScriptAliases. Checks {@link ParserInstance#isActive()}.
	 * @return The obtained aliases, or null if the script has no custom aliases.
	 */
	@Nullable
	private static ScriptAliases getScriptAliases() {
		ParserInstance parser = ParserInstance.get();
		if (parser.isActive())
			return getScriptAliases(parser.getCurrentScript());
		return null;
	}

	/**
	 * Method for obtaining the ScriptAliases instance of a {@link Script}.
	 * @param script The script to obtain aliases from.
	 * @return The obtained aliases, or null if the script has no custom aliases.
	 */
	@Nullable
	public static ScriptAliases getScriptAliases(Script script) {
		return script.getData(ScriptAliases.class);
	}

}
