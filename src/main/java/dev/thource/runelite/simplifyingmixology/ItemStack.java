package dev.thource.runelite.simplifyingmixology;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** ItemStack represents an OSRS item with a quantity. */
@Getter
@Setter
@EqualsAndHashCode
public class ItemStack {

  private int id;
  private long quantity;
  private int slot;

  public ItemStack(int id, long quantity, int slot) {
    this.id = id;
    this.quantity = quantity;
    this.slot = slot;
  }

  public ItemStack(ItemStack itemStack) {
    this(itemStack.getId(), itemStack.getQuantity(), itemStack.getSlot());
  }
}
