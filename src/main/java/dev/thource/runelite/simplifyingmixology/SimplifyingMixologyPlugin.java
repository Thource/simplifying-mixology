package dev.thource.runelite.simplifyingmixology;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** SimplifyingMixologyPlugin is a RuneLite plugin designed to blah. */
@Slf4j
@PluginDescriptor(
    name = "Simplifying Mixology",
    description = "basfas.",
    tags = {"basga"})
public class SimplifyingMixologyPlugin extends Plugin {

  @Getter @Inject private Client client;
  @Getter @Inject private SimplifyingMixologyConfig config;

  @Override
  protected void startUp() {
    // blah
  }

  @Override
  protected void shutDown() {
    // blah
  }  // Use a high priority so that this is called way after any other plugins add menu entries

  @Subscribe(priority = 999)
  public void onMenuEntryAdded(MenuEntryAdded event) {
    var menuEntry = event.getMenuEntry();

    if (menuEntry.getOption().equals("Fulfil-order") && menuEntry.getTarget().equals("<col=ffff>Conveyor belt")) {
      menuEntry.setDeprioritized(shouldHideFulfil());
    }
  }

  private boolean shouldHideFulfil() {
    log.info("order1 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_TYPE));
    log.info("order1 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_1_MODIFIER));
    log.info("order2 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_TYPE));
    log.info("order1 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_2_MODIFIER));
    log.info("order3 type: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_TYPE));
    log.info("order1 mod: {}", client.getVarbitValue(VarbitID.MM_LAB_ORDER_3_MODIFIER));

    return true;
  }

  @Provides
  SimplifyingMixologyConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(SimplifyingMixologyConfig.class);
  }
}
