package dev.thource.runelite.simplifyingmixology;

import java.util.List;
import java.util.ListIterator;

/** ItemStackUtils provides methods that interact with Lists of ItemStack. */
public class ItemStackUtils {

  /**
   * Removes an ItemStack from the supplied List of ItemStacks. Any removed ItemStacks will be
   * replaced with empty slots.
   *
   * @param items ItemStacks to remove from
   * @param itemToRemove ItemStack to remove
   */
  public static void removeItemStack(List<ItemStack> items, ItemStack itemToRemove) {
    if (itemToRemove.getId() == -1) {
      return;
    }

    long quantityToRemove = itemToRemove.getQuantity();

    ListIterator<ItemStack> listIterator = items.listIterator();
    while (listIterator.hasNext() && quantityToRemove > 0) {
      ItemStack inventoryItem = listIterator.next();

      if (inventoryItem.getId() != itemToRemove.getId()) {
        continue;
      }

      long qtyToRemove = Math.min(quantityToRemove, inventoryItem.getQuantity());
      quantityToRemove -= qtyToRemove;
      inventoryItem.setQuantity(inventoryItem.getQuantity() - qtyToRemove);
      if (inventoryItem.getQuantity() == 0) {
        listIterator.set(new ItemStack(-1, -1));
      }
    }
  }

  /**
   * Removes a list of items from another list of items.
   *
   * @param itemsToModify the list of items to remove from
   * @param itemsToRemove the list of items to be removed
   */
  public static void removeItems(List<ItemStack> itemsToModify, List<ItemStack> itemsToRemove) {
    for (ItemStack itemStack : itemsToRemove) {
      ItemStackUtils.removeItemStack(itemsToModify, itemStack);
    }
  }
}
