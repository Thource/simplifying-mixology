package dev.thource.runelite.simplifyingmixology;

import java.awt.Color;
import java.awt.Graphics2D;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class InventoryPotionOverlay extends WidgetItemOverlay {
  private final SimplifyingMixologyPlugin plugin;
  private final SimplifyingMixologyConfig config;

  @Inject
  InventoryPotionOverlay(SimplifyingMixologyPlugin plugin, SimplifyingMixologyConfig config) {
    this.plugin = plugin;
    this.config = config;
    showOnInventory();
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics2D, int itemId, WidgetItem widgetItem) {
    //        if (!plugin.isInLab() || config.inventoryPotionTagType() ==
    // InventoryPotionTagType.NONE) {
    //            return;
    //        }

    //    if (PotionType.fromItemId(itemId) == null) {
    //      return;
    //    }

    var modifier = plugin.getPotionModifiers()[widgetItem.getWidget().getIndex()];
    var bounds = widgetItem.getCanvasBounds();
    var x = bounds.x + 15;
    var y = bounds.y + 10;

    drawModifier(graphics2D, modifier, x + 1, y + 1, Color.BLACK); // Drop shadow
    drawModifier(
        graphics2D, modifier, x, y, modifier == PotionModifier.UNKNOWN ? Color.RED : Color.WHITE);
  }

  private void drawModifier(
      Graphics2D graphics2D, PotionModifier modifier, int x, int y, @Nullable Color color) {
    graphics2D.setFont(FontManager.getRunescapeSmallFont());

    graphics2D.setColor(color);
    graphics2D.drawString(modifier.getShortText(), x, y);
  }
}
