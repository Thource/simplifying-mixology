package dev.thource.runelite.simplifyingmixology;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/** SimplifyingMixologyPlugin is a RuneLite plugin designed to blah. */
@Slf4j
@PluginDescriptor(
    name = "Simplifying Mixology",
    description = "basfas.",
    tags = {"basga"})
public class SimplifyingMixologyPlugin extends Plugin {

  @Getter @Inject private Client client;
  @Getter @Inject private ClientThread clientThread;
  @Getter @Inject private OverlayManager overlayManager;

  @Getter @Inject private SimplifyingMixologyConfig config;
  @Getter @Inject private InventoryPotionOverlay inventoryPotionOverlay;

  @Getter private final PotionModifier[] potionModifiers = new PotionModifier[28];
  private int lastInspectedSlot = -1;
  private final ItemContainerWatcher inventoryWatcher  = ItemContainerWatcher.getInventoryWatcher();

  @Override
  protected void startUp() {
    Arrays.fill(potionModifiers, PotionModifier.UNKNOWN);

    ItemContainerWatcher.init(client);

    overlayManager.add(inventoryPotionOverlay);
  }

  @Override
  protected void shutDown() {
    overlayManager.remove(inventoryPotionOverlay);

    ItemContainerWatcher.reset();
  }

  @Subscribe
  void onGameTick(GameTick gameTick) {
    ItemContainerWatcher.onGameTick();

    if (inventoryWatcher.wasJustUpdated()) {
      var itemsAddedLastTick = inventoryWatcher.getItemsAddedLastTick();
      var itemsRemovedLastTick = inventoryWatcher.getItemsRemovedLastTick();

      itemsAddedLastTick.stream().filter(addedItem -> PotionType.fromItemId(addedItem.getId()) != null).forEach(addedItem -> {
        itemsRemovedLastTick.stream().filter(removedItem -> removedItem.getId() == addedItem.getId()).findFirst().ifPresent(removedItem -> {
          potionModifiers[addedItem.getSlot()] = potionModifiers[removedItem.getSlot()];
          potionModifiers[removedItem.getSlot()] = PotionModifier.UNKNOWN;
        })
      });
    }
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged event) {
    if (event.getContainerId() != InventoryID.INV) {
      return;
    }
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked event) {
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
    var menuEntry = event.getMenuEntry();

    if (menuEntry.getOption().equals("Fulfil-order")
        && menuEntry.getTarget().equals("<col=ffff>Conveyor belt")) {
      menuEntry.setDeprioritized(shouldHideFulfil());
    }
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded event) {
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

  private boolean shouldHideFulfil() {
    //    log.info("order1 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_TYPE));
    //    log.info("order1 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_MODIFIER));
    //    log.info("order2 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_TYPE));
    //    log.info("order2 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_MODIFIER));
    //    log.info("order3 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_TYPE));
    //    log.info("order3 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_MODIFIER));

    return true;
  }

  @Provides
  SimplifyingMixologyConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(SimplifyingMixologyConfig.class);
  }
}
