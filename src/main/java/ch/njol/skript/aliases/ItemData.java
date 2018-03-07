/**
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Copyright 2011-2017 Peter Güttinger and contributors
 */
package ch.njol.skript.aliases;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.UnsafeValues;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.Gson;

import ch.njol.skript.bukkitutil.block.BlockCompat;
import ch.njol.skript.bukkitutil.block.BlockValues;
import ch.njol.skript.localization.Message;
import ch.njol.skript.util.Utils;
import ch.njol.skript.variables.Variables;
import ch.njol.util.coll.CollectionUtils;
import ch.njol.util.coll.iterator.SingleItemIterator;
import ch.njol.yggdrasil.Fields;
import ch.njol.yggdrasil.YggdrasilSerializable;
import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilExtendedSerializable;

@SuppressWarnings("deprecation")
public class ItemData implements Cloneable, YggdrasilExtendedSerializable {
	
	static {
		Variables.yggdrasil.registerSingleClass(ItemData.class, "NewItemData");
		Variables.yggdrasil.registerSingleClass(OldItemData.class, "ItemData");
	}
	
	/**
	 * Represents old ItemData (before aliases rework and MC 1.13).
	 */
	public static class OldItemData {
		
		int typeid = -1;
		public short dataMin = -1;
		public short dataMax = -1;
	}
	
	@SuppressWarnings("null")
	private static final UnsafeValues unsafe = Bukkit.getUnsafe();
	
	private static final Gson gson = new Gson();
	@SuppressWarnings("null")
	static final ItemFactory itemFactory = Bukkit.getServer().getItemFactory();
	
	private final static Message m_named = new Message("aliases.named");
	
	/**
	 * ItemStack, which is used for everything but serialization.
	 */
	transient ItemStack stack;
	
	/**
	 * ItemMeta of stack.
	 */
	ItemMeta meta;
	
	/**
	 * Type of the item as Bukkit material.
	 */
	Material type;
	
	/**
	 * If this represents all possible items.
	 */
	boolean isAnything;
	
	/**
	 * When this ItemData represents a block, this contains information to
	 * allow comparing it against other blocks.
	 */
	@Nullable
	transient BlockValues blockValues;
	
	public ItemData(Material type, @Nullable String tags) {
		this.type = type;
		
		this.stack = new ItemStack(type);
		unsafe.modifyItemStack(stack, tags);
		assert stack != null; // Yeah nope; modifyItemStack is not THAT Unsafe
		
		// Initialize block values with a terrible hack
		this.blockValues = BlockCompat.INSTANCE.getBlockValues(stack);
		
		// Grab item meta (may be null)
		assert stack != null;
		this.meta = stack.getItemMeta();
	}
	
	public ItemData(Material type, int amount) {
		this.type = type;
		
		this.stack = new ItemStack(type, Math.abs(amount));
		blockValues = BlockCompat.INSTANCE.getBlockValues(stack);
		this.meta = itemFactory.getItemMeta(type);
	}
	
	public ItemData(Material type) {
		this(type, 1);
	}
	
	public ItemData(ItemData data) {
		this(data.stack);
	}
	
	public ItemData(ItemStack stack) {
		this.stack = stack;
		this.type = stack.getType();
		this.blockValues = BlockCompat.INSTANCE.getBlockValues(stack); // Grab block values from stack
		this.meta = stack.getItemMeta(); // Grab meta from stack
	}
	
	public ItemData(BlockState block) {
		this.type = block.getType();
		this.stack = new ItemStack(type);
		this.blockValues = BlockCompat.INSTANCE.getBlockValues(block);
		this.meta = stack.getItemMeta();
	}
	
	public ItemData(Block block) {
		this(block.getState());
	}
	
	/**
	 * Only to be used for serialization.
	 */
	@SuppressWarnings("null") // Yeah, only for internal use
	public ItemData() {}
	
	/**
	 * Tests whether the given item is of this type.
	 * 
	 * @param item
	 * @return Whether the given item is of this type.
	 */
	public boolean isOfType(@Nullable ItemStack item) {
		if (item == null)
			return type == Material.AIR;
		return item.isSimilar(stack);
	}
	
	public boolean isSupertypeOf(ItemData o) {
		// Since numeric ids are not used anymore, supertype-ness is based on aliases
		return Aliases.isSupertypeOf(this, o);
	}
	
	/**
	 * Returns <code>Aliases.{@link Aliases#getMaterialName(ItemData, boolean) getMaterialName}(ItemData, boolean)</code>
	 * called with this object and relevant plurarily setting.
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(Aliases.getMaterialName(this, false));
		if (meta.hasDisplayName()) {
			builder.append(" ").append(m_named).append(" ");
			builder.append(meta.getDisplayName());
		}
		return builder.toString();
	}
	
	public String toString(final boolean debug, final boolean plural) {
		return toString(); // TODO better debug info
	}
	
	/**
	 * @return The item's gender or -1 if no name is found
	 */
	public int getGender() {
		return Aliases.getGender(this);
	}
	
	@Override
	public boolean equals(final @Nullable Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof ItemData))
			return false;
		
		ItemData other = (ItemData) obj;
		if (isAnything || other.isAnything) // First, isAnything check
			return true;
		
		return other.stack.isSimilar(stack);
	}
	
	@Override
	public int hashCode() {
		return stack.hashCode();
	}
	
	/**
	 * Computes the intersection of two ItemDatas. The data range of the returned item data will be the real intersection of the two data ranges, and the type id will be the one
	 * set if any.
	 * 
	 * @param other
	 * @return A new ItemData which is the intersection of the given types, or null if the intersection of the data ranges is empty or both datas have an id != -1 which are not the
	 *         same.
	 */
	@Nullable
	public ItemData intersection(final ItemData other) {
		if (other.type != type) // Different type, no intersection possible
			return null;
		
		// TODO implement meta intersection
		return null;
	}
	
	/**
	 * Returns the ItemStack backing this ItemData.
	 * It is not a copy, so please be careful.
	 * @return Item stack.
	 */
	public ItemStack getStack() {
		return stack;
	}
	
	@Override
	public ItemData clone() {
		return new ItemData(this);
	}
	
	public Material getType() {
		return type;
	}
	
	@Nullable
	public BlockValues getBlockValues() {
		return blockValues;
	}
	
	public ItemMeta getItemMeta() {
		return meta;
	}
	
	public void setItemMeta(ItemMeta meta) {
		this.meta = meta;
		stack.setItemMeta(meta);
		if (blockValues != null) { // If block values exist, update them from stack
			assert stack != null;
			blockValues = BlockCompat.INSTANCE.getBlockValues(stack);
		}
	}

	@Override
	public Fields serialize() throws NotSerializableException {
		return new Fields(this); // ItemStack is transient, will be ignored
	}

	@Override
	public void deserialize(Fields fields) throws StreamCorruptedException, NotSerializableException {
		fields.setFields(this); // Everything but ItemStack
		
		// Initialize ItemStack
		this.stack = new ItemStack(type);
		stack.setItemMeta(meta); // Just set meta to it
		
		// Initialize block values with a terrible hack
		this.blockValues = BlockCompat.INSTANCE.getBlockValues(stack);
	}
	
}