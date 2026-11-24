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

  @Inject
  InventoryPotionOverlay(SimplifyingMixologyPlugin plugin) {
    this.plugin = plugin;
    showOnInventory();
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics2D, int itemId, WidgetItem widgetItem) {
    if (!plugin.isInLab()) {
      return;
    }

    var bounds = widgetItem.getCanvasBounds();
    var index = widgetItem.getWidget().getIndex();
    if (index >= 0 && index < plugin.getImproved().length && plugin.getImproved()[index]) {
      drawDigweed(graphics2D, bounds);
    }

    if (PotionType.fromItemId(itemId) == null) {
      return;
    }

    var modifier = plugin.getPotionModifiers()[index];
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

  private void drawDigweed(Graphics2D graphics2D, java.awt.Rectangle bounds) {
    // digweed has been used on this potion
    // centred in the slot, clear of the method text at the top and other plugins' text at the bottom
    graphics2D.setFont(FontManager.getRunescapeSmallFont());
    var metrics = graphics2D.getFontMetrics();
    var dx = bounds.x + (bounds.width - metrics.stringWidth("DW")) / 2;
    var dy = bounds.y + (bounds.height + metrics.getAscent()) / 2;
    graphics2D.setColor(Color.BLACK);
    graphics2D.drawString("DW", dx + 1, dy + 1);
    graphics2D.setColor(new Color(0x5BE36B));
    graphics2D.drawString("DW", dx, dy);
  }
}
