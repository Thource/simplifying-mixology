package dev.thource.runelite.simplifyingmixology;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
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
  private int lastInspectedSlot = -1;
  private PotionType agitatorPotionType;
  private PotionType alembicPotionType;
  private PotionType retortPotionType;
  private PotionType checkForPotionType;
  private PotionModifier checkForPotionModifier;
  @Getter private boolean inLab;

  @Override
  protected void startUp() {
    Arrays.fill(potionModifiers, PotionModifier.UNKNOWN);

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

    checkForPotionModify();

    var items = event.getItemContainer().getItems();
    for (int slot = 0; slot < items.length; slot++) {
      if (potionModifiers[slot] != PotionModifier.UNKNOWN
          && PotionType.fromItemId(items[slot].getId()) == null) {
        potionModifiers[slot] = PotionModifier.UNKNOWN;
      }
    }
  }

  private void onPotionModified(PotionType potionType, PotionModifier potionModifier) {
    if (potionType == null) {
      return;
    }

    checkForPotionType = potionType;
    checkForPotionModifier = potionModifier;
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

    var value = event.getValue();
    if (varbitId == VarbitID.MM_LAB_AGITATOR_POTION) {
      if (value == 0) {
        onPotionModified(agitatorPotionType, PotionModifier.HOMOGENOUS);
        agitatorPotionType = null;
      } else {
        agitatorPotionType = PotionType.fromIndex(value - 1);
      }
    } else if (varbitId == VarbitID.MM_LAB_ALEMBIC_POTION) {
      if (value == 0) {
        onPotionModified(alembicPotionType, PotionModifier.CRYSTALISED);
        alembicPotionType = null;
      } else {
        alembicPotionType = PotionType.fromIndex(value - 1);
      }
    } else if (varbitId == VarbitID.MM_LAB_RETORT_POTION) {
      if (value == 0) {
        onPotionModified(retortPotionType, PotionModifier.CONCENTRATED);
        retortPotionType = null;
      } else {
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

    if (menuEntry.getOption().equals("Fulfil-order")
        && menuEntry.getTarget().equals("<col=ffff>Conveyor belt")) {
      menuEntry.setDeprioritized(shouldHideFulfil());
    }
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
