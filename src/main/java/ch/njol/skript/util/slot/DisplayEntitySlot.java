package ch.njol.skript.util.slot;

import ch.njol.skript.registrations.Classes;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DisplayEntitySlot extends Slot {

	private final ItemDisplay display;

	public DisplayEntitySlot(ItemDisplay display) {
		this.display = display;
	}

	@Override
	public @Nullable ItemStack getItem() {
		return display.getItemStack();
	}

	@Override
	public void setItem(@Nullable ItemStack item) {
		display.setItemStack(item);
	}

	@Override
	public int getAmount() {
		return 1;
	}

	@Override
	public void setAmount(int amount) {}

	public ItemDisplay getItemDisplay() {
		return display;
	}

	@Override
	public boolean isSameSlot(Slot slot) {
		return slot instanceof DisplayEntitySlot displayEntitySlot
			&& displayEntitySlot.getItemDisplay().equals(display);
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return Classes.toString(getItem());
	}

}
