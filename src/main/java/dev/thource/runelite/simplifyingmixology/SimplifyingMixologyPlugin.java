package dev.thource.runelite.simplifyingmixology;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * SimplifyingMixologyPlugin is a RuneLite plugin designed to simplify Mastering Mixology by making
 * it harder to deposit wrong orders.
 */
@Slf4j
@PluginDescriptor(
    name = "Simplifying Mixology",
    description =
        "Deposit wrong orders no more, Simplifying Mixology turns fulfil-order into a right click"
            + " option if you don't have a potion from an active order.",
    tags = {"mastering", "mixology", "order"})
public class SimplifyingMixologyPlugin extends Plugin {

  @Getter @Inject private Client client;
  @Getter @Inject private ClientThread clientThread;
  @Getter @Inject private OverlayManager overlayManager;

  @Getter @Inject private SimplifyingMixologyConfig config;
  @Getter @Inject private InventoryPotionOverlay inventoryPotionOverlay;

  @Getter private final PotionModifier[] potionModifiers = new PotionModifier[28];
  // true when a digweed has been used on the potion in that slot (tracked, or read from Inspect)
  @Getter private final boolean[] improved = new boolean[28];
  private int pendingImproveSlot = -1;
  private int lastDigweedCount = -1;
  // item id per slot last time we looked, to notice marked items leaving their slot
  private final int[] prevIds = new int[28];
  // digweed carried through a station (agitator, alembic, retort)
  private final boolean[] stationImproved = new boolean[3];
  private boolean checkForPotionImproved;
  private int lastImprovedRemovedTick = -5;
  private int lastStationStart = -1;
  private int lastStationStartTick = -5;
  private int lastInspectedSlot = -1;
  private PotionType agitatorPotionType;
  private PotionType alembicPotionType;
  private PotionType retortPotionType;
  private PotionType checkForPotionType;
  private PotionModifier checkForPotionModifier;
  @Getter private boolean inLab;

  // Set when an order is handed in; cleared when the new orders arrive (or after a short timeout).
  // While set, further Fulfil-order clicks are blocked so they can't land on orders we haven't seen.
  private boolean awaitingNewOrders;
  private int ticksAwaitingOrders;
  private static final int MAX_TICKS_AWAITING_ORDERS = 2;
  // Set when a hand-in has been clicked; the guard only starts once potions actually leave the
  // inventory, so walking to the conveyor doesn't use up the timeout.
  private boolean handInClicked;
  private int ticksSinceHandInClick;
  // If no hand-in happens this long after the click (walked away, cancelled), stop waiting.
  private static final int MAX_TICKS_PENDING_HAND_IN = 15;
  private int lastPotionCount = -1;
  private int lastOrderChangeTick = -1;

  @Override
  protected void startUp() {
    Arrays.fill(potionModifiers, PotionModifier.UNKNOWN);
    Arrays.fill(prevIds, -1);

    overlayManager.add(inventoryPotionOverlay);

    if (client.getGameState() == GameState.LOGGED_IN) {
      clientThread.invokeLater(this::initialize);
    }
  }

  private void initialize() {
    var ordersLayer = client.getWidget(InterfaceID.MM_OVERLAY, 0);
    if (ordersLayer == null || ordersLayer.isSelfHidden()) {
      return;
    }

    inLab = true;
  }

  @Override
  protected void shutDown() {
    overlayManager.remove(inventoryPotionOverlay);
    inLab = false;
    awaitingNewOrders = false;
    handInClicked = false;
    lastPotionCount = -1;
    pendingImproveSlot = -1;
    lastDigweedCount = -1;
    Arrays.fill(improved, false);
    Arrays.fill(stationImproved, false);
    Arrays.fill(prevIds, -1);
  }

  @Subscribe
  void onScriptPreFired(ScriptPreFired event) {
    if (!inLab) {
      return;
    }

    // inventory item re-order
    if (event.getScriptId() == 6013) {
      var slot1 = event.getScriptEvent().getSource().getIndex();
      var target = event.getScriptEvent().getTarget();
      if (target == null) {
        return;
      }

      var slot2 = target.getIndex();
      var temp = potionModifiers[slot1];
      potionModifiers[slot1] = potionModifiers[slot2];
      potionModifiers[slot2] = temp;
      var tempImproved = improved[slot1];
      improved[slot1] = improved[slot2];
      improved[slot2] = tempImproved;
      var tempId = prevIds[slot1];
      prevIds[slot1] = prevIds[slot2];
      prevIds[slot2] = tempId;
    }
  }

  // An improved item left the inventory: if a station started at the same time, it went in there.
  private void onImprovedRemoved() {
    var tick = client.getTickCount();
    if (lastStationStart != -1 && lastStationStartTick >= tick - 1) {
      stationImproved[lastStationStart] = true;
      lastStationStart = -1;
    } else {
      lastImprovedRemovedTick = tick;
    }
  }

  private void onStationStarted(int station) {
    var tick = client.getTickCount();
    stationImproved[station] = false;
    if (lastImprovedRemovedTick >= tick - 1) {
      stationImproved[station] = true;
      lastImprovedRemovedTick = -5;
    } else {
      lastStationStart = station;
      lastStationStartTick = tick;
    }
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged event) {
    if (!inLab) {
      return;
    }

    if (event.getContainerId() != InventoryID.INV) {
      return;
    }

    var items = event.getItemContainer().getItems();

    // Follow digweed marks: when a marked item leaves its slot, it was used up, handed in or put
    // into a station (onImprovedRemoved works out which).
    for (int slot = 0; slot < items.length && slot < prevIds.length; slot++) {
      var id = items[slot].getId();
      if (prevIds[slot] != -1 && id != prevIds[slot] && improved[slot]) {
        improved[slot] = false;
        onImprovedRemoved();
      }
      prevIds[slot] = id;
    }

    int potionCount = 0;
    for (var item : items) {
      if (PotionType.fromItemId(item.getId()) != null) {
        potionCount++;
      }
    }
    if (handInClicked && lastPotionCount != -1 && potionCount < lastPotionCount) {
      handInClicked = false;
      // If the new orders already arrived this tick, there's nothing to wait for.
      if (lastOrderChangeTick != client.getTickCount()) {
        awaitingNewOrders = true;
        ticksAwaitingOrders = 0;
      }
    }
    lastPotionCount = potionCount;

    int digweedCount = 0;
    for (var item : items) {
      if (item.getId() > 0 && "Digweed".equals(client.getItemDefinition(item.getId()).getName())) {
        digweedCount += item.getQuantity();
      }
    }
    if (pendingImproveSlot != -1 && lastDigweedCount != -1 && digweedCount < lastDigweedCount) {
      // finished potions and unprocessed bases both count
      if (pendingImproveSlot >= 0 && pendingImproveSlot < items.length
          && items[pendingImproveSlot].getId() > 0) {
        improved[pendingImproveSlot] = true;
      }
      pendingImproveSlot = -1;
    }
    lastDigweedCount = digweedCount;

    for (int slot = 0; slot < items.length; slot++) {
      if (PotionType.fromItemId(items[slot].getId()) == null) {
        potionModifiers[slot] = PotionModifier.UNKNOWN;
      }
      if (items[slot].getId() <= 0) {
        improved[slot] = false;
      }
    }

    // after the loops above, so the finished potion's mark isn't mistaken for a removed one
    checkForPotionModify();
  }

  private void onPotionModified(PotionType potionType, PotionModifier potionModifier, int station) {
    if (potionType == null) {
      return;
    }

    checkForPotionType = potionType;
    checkForPotionModifier = potionModifier;
    checkForPotionImproved = stationImproved[station];
    stationImproved[station] = false;
  }

  private void checkForPotionModify() {
    if (checkForPotionType == null) {
      return;
    }

    var inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) {
      return;
    }

    var items = inventory.getItems();
    for (int slot = 0; slot < items.length; slot++) {
      var item = items[slot];
      if (item.getId() == checkForPotionType.getItemId()
          && potionModifiers[slot] == PotionModifier.UNKNOWN) {
        potionModifiers[slot] = checkForPotionModifier;
        improved[slot] = checkForPotionImproved;
        prevIds[slot] = item.getId();
        break;
      }
    }

    checkForPotionType = null;
    checkForPotionModifier = null;
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged event) {
    if (!inLab) {
      return;
    }

    var varbitId = event.getVarbitId();
    if (varbitId == -1) {
      return;
    }

    if (varbitId == VarbitID.MM_LAB_ORDER_1_TYPE
        || varbitId == VarbitID.MM_LAB_ORDER_2_TYPE
        || varbitId == VarbitID.MM_LAB_ORDER_3_TYPE
        || varbitId == VarbitID.MM_LAB_ORDER_1_MODIFIER
        || varbitId == VarbitID.MM_LAB_ORDER_2_MODIFIER
        || varbitId == VarbitID.MM_LAB_ORDER_3_MODIFIER) {
      awaitingNewOrders = false;
      handInClicked = false;
      lastOrderChangeTick = client.getTickCount();
    }

    var value = event.getValue();
    if (varbitId == VarbitID.MM_LAB_AGITATOR_POTION) {
      if (value == 0) {
        onPotionModified(agitatorPotionType, PotionModifier.HOMOGENOUS, 0);
        agitatorPotionType = null;
      } else {
        if (agitatorPotionType == null) {
          onStationStarted(0);
        }
        agitatorPotionType = PotionType.fromIndex(value - 1);
      }
    } else if (varbitId == VarbitID.MM_LAB_ALEMBIC_POTION) {
      if (value == 0) {
        onPotionModified(alembicPotionType, PotionModifier.CRYSTALISED, 1);
        alembicPotionType = null;
      } else {
        if (alembicPotionType == null) {
          onStationStarted(1);
        }
        alembicPotionType = PotionType.fromIndex(value - 1);
      }
    } else if (varbitId == VarbitID.MM_LAB_RETORT_POTION) {
      if (value == 0) {
        onPotionModified(retortPotionType, PotionModifier.CONCENTRATED, 2);
        retortPotionType = null;
      } else {
        if (retortPotionType == null) {
          onStationStarted(2);
        }
        retortPotionType = PotionType.fromIndex(value - 1);
      }
    }
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked event) {
    if (!inLab) {
      return;
    }

    var menuEntry = event.getMenuEntry();

    if (menuEntry.getOption().equals("Use") && menuEntry.getTarget().contains("Digweed")
        && menuEntry.getTarget().contains("->")) {
      var target = menuEntry.getTarget();
      if (target.substring(target.indexOf("->")).contains("Digweed")) {
        // potion used on digweed: the potion is the selected item
        var selected = client.getSelectedWidget();
        pendingImproveSlot = selected != null ? selected.getIndex() : -1;
      } else {
        // digweed used on potion: the potion is the clicked item
        pendingImproveSlot = menuEntry.getParam0();
      }
      return;
    }

    if (isConveyorFulfil(menuEntry)) {
      if (awaitingNewOrders || handInClicked) {
        // A hand-in is already queued or hasn't produced new orders yet. The server would run
        // this click on the next tick against the new orders, so drop it.
        event.consume();
        return;
      }

      handInClicked = true;
      ticksSinceHandInClick = 0;
      return;
    }

    if (menuEntry.getOption().equals("Use") && menuEntry.getTarget().contains("Conveyor belt")) {
      // a deliberate dump also resets the orders
      handInClicked = true;
      ticksSinceHandInClick = 0;
      return;
    }

    if (!menuEntry.getOption().equals("Inspect")) {
      handInClicked = false;
    }

    if (!menuEntry.getOption().equals("Inspect")) {
      return;
    }

    var itemId = event.getItemId();
    var potionType = PotionType.fromItemId(itemId);
    if (potionType == null) {
      return;
    }

    lastInspectedSlot = menuEntry.getParam0();
  }

  @Subscribe
  public void onMenuEntryAdded(MenuEntryAdded event) {
    if (!inLab) {
      return;
    }

    var menuEntry = event.getMenuEntry();

    if (isConveyorFulfil(menuEntry)) {
      menuEntry.setDeprioritized(awaitingNewOrders || handInClicked || shouldHideFulfil());
    }
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    if (handInClicked && ++ticksSinceHandInClick >= MAX_TICKS_PENDING_HAND_IN) {
      handInClicked = false;
    }
    if (awaitingNewOrders && ++ticksAwaitingOrders >= MAX_TICKS_AWAITING_ORDERS) {
      // New orders can be identical to the old ones, in which case no varbit changes.
      awaitingNewOrders = false;
    }
  }

  private static boolean isConveyorFulfil(net.runelite.api.MenuEntry menuEntry) {
    return menuEntry.getOption().equals("Fulfil-order")
        && menuEntry.getTarget().equals("<col=ffff>Conveyor belt");
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded event) {
    if (event.getGroupId() == InterfaceID.MM_OVERLAY) {
      inLab = true;
      return;
    }

    if (!inLab) {
      return;
    }

    if (lastInspectedSlot == -1) {
      return;
    }

    if (event.getGroupId() == InterfaceID.OBJECTBOX) {
      var textBox = client.getWidget(InterfaceID.Objectbox.TEXT);
      if (textBox != null) {
        clientThread.invokeLater(
            () -> {
              var text = textBox.getText();
              if (text.startsWith("It's a vial of")) {
                potionModifiers[lastInspectedSlot] = PotionModifier.fromText(text);
                // the game wraps this line with a <br> and colour tags, so normalise before matching
                improved[lastInspectedSlot] =
                    Text.removeTags(text.replace("<br>", " "))
                        .replaceAll("\\s+", " ")
                        .contains("quality has been improved");
                lastInspectedSlot = -1;
              }
            });
      }
    }
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed event) {
    if (event.getGroupId() != InterfaceID.MM_OVERLAY) {
      return;
    }

    inLab = false;
  }

  private boolean shouldHideFulfil() {
    var inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) {
      return true;
    }

    var order1Type = PotionType.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_TYPE) - 1);
    var order1Modifier =
        PotionModifier.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_MODIFIER));
    var order2Type = PotionType.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_TYPE) - 1);
    var order2Modifier =
        PotionModifier.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_MODIFIER));
    var order3Type = PotionType.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_TYPE) - 1);
    var order3Modifier =
        PotionModifier.fromIndex(client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_MODIFIER));

    var items = inventory.getItems();
    for (int slot = 0; slot < items.length; slot++) {
      var item = items[slot];
      var modifier = potionModifiers[slot];

      if (order1Modifier == modifier) {
        if (order1Type != null && item.getId() == order1Type.getItemId()) {
          return false;
        }
      }

      if (order2Modifier == modifier) {
        if (order2Type != null && item.getId() == order2Type.getItemId()) {
          return false;
        }
      }

      if (order3Modifier == modifier) {
        if (order3Type != null && item.getId() == order3Type.getItemId()) {
          return false;
        }
      }
    }

    return true;
  }

  @Provides
  SimplifyingMixologyConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(SimplifyingMixologyConfig.class);
  }
}
