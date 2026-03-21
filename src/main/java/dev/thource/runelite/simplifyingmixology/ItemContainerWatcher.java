package dev.thource.runelite.simplifyingmixology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/** ItemContainerWatcher makes it easy to detect changes to ItemContainers. */
public class ItemContainerWatcher {

  @Getter
  private static final ItemContainerWatcher inventoryWatcher =
      new ItemContainerWatcher(InventoryID.INV, true);

  private static Client client;

  private final int itemContainerId;
  private final List<ItemStack> itemsLastTick = new ArrayList<>();
  @Getter private final List<ItemStack> items = new ArrayList<>();
  private boolean justUpdated = false;
  private final boolean requiresInitialUpdate;
  private boolean initialUpdateOccurred = false;

  ItemContainerWatcher(int itemContainerId) {
    this.itemContainerId = itemContainerId;
    this.requiresInitialUpdate = false;
  }

  ItemContainerWatcher(int itemContainerId, boolean requiresInitialUpdate) {
    this.itemContainerId = itemContainerId;
    this.requiresInitialUpdate = requiresInitialUpdate;
  }

  static void init(Client client) {
    ItemContainerWatcher.client = client;
  }

  static void reset() {
    inventoryWatcher.itemsLastTick.clear();
    inventoryWatcher.items.clear();
    inventoryWatcher.justUpdated = false;
    if (inventoryWatcher.requiresInitialUpdate) {
      inventoryWatcher.initialUpdateOccurred = false;
    }
  }

  static void onGameTick() {
    inventoryWatcher.gameTick();
  }

  public boolean wasJustUpdated() {
    return justUpdated;
  }

  private void gameTick() {
    justUpdated = false;
    itemsLastTick.clear();
    itemsLastTick.addAll(items);

    ItemContainer itemContainer = client.getItemContainer(itemContainerId);
    if (itemContainer == null) {
      return;
    }

    justUpdated = true;
    items.clear();
    var containerItems = itemContainer.getItems();
    for (int slot = 0; slot < containerItems.length; slot++) {
      var item = containerItems[slot];
      items.add(new ItemStack(item.getId(), item.getQuantity(), slot));
    }

    if (requiresInitialUpdate && !initialUpdateOccurred) {
      initialUpdateOccurred = true;

      itemsLastTick.clear();
      itemsLastTick.addAll(items);
    }
  }

  // todo: function that returns a list of items that have been added or removed

  /**
   * returns a List of just added ItemStacks.
   *
   * @return ItemStack List
   */
  public List<ItemStack> getItemsAddedLastTick() {
    if (!justUpdated || (requiresInitialUpdate && !initialUpdateOccurred)) {
      return List.of();
    }

    List<ItemStack> itemsAddedLastTick =
        items.stream()
            .filter(i -> i.getId() != -1)
            .map(ItemStack::new)
            .collect(Collectors.toList());

    ItemStackUtils.removeItems(itemsAddedLastTick, itemsLastTick);

    return itemsAddedLastTick;
  }

  /**
   * returns a List of just removed ItemStacks.
   *
   * @return ItemStack List
   */
  public List<ItemStack> getItemsRemovedLastTick() {
    if (!justUpdated || (requiresInitialUpdate && !initialUpdateOccurred)) {
      return List.of();
    }

    List<ItemStack> itemsRemovedLastTick =
        itemsLastTick.stream()
            .filter(i -> i.getId() != -1)
            .map(ItemStack::new)
            .collect(Collectors.toList());

    ItemStackUtils.removeItems(itemsRemovedLastTick, items);

    return itemsRemovedLastTick;
  }
}
