package dev.thource.runelite.simplifyingmixology;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@RequiredArgsConstructor
@Getter
public enum PotionType {
  MAMMOTH_MIGHT_MIX(ItemID.MM_POTION_MMM_FINISHED),
  MYSTIC_MANA_AMALGAM(ItemID.MM_POTION_MMA_FINISHED),
  MARLEYS_MOONLIGHT(ItemID.MM_POTION_MML_FINISHED),
  ALCO_AUGMENTATOR(ItemID.MM_POTION_AAA_FINISHED),
  AZURE_AURA_MIX(ItemID.MM_POTION_AAM_FINISHED),
  AQUALUX_AMALGAM(ItemID.MM_POTION_AAL_FINISHED),
  LIPLACK_LIQUOR(ItemID.MM_POTION_LLL_FINISHED),
  MEGALITE_LIQUID(ItemID.MM_POTION_LLM_FINISHED),
  ANTI_LEECH_LOTION(ItemID.MM_POTION_LLA_FINISHED),
  MIXALOT(ItemID.MM_POTION_MAL_FINISHED);

  public static final PotionType[] TYPES = PotionType.values();

  private static final Map<Integer, PotionType> ITEM_MAP;

  static {
    var builder = new ImmutableMap.Builder<Integer, PotionType>();
    for (var p : PotionType.values()) {
      builder.put(p.getItemId(), p);
    }
    ITEM_MAP = builder.build();
  }

  private final int itemId;

  public static PotionType fromItemId(int itemId) {
    return ITEM_MAP.get(itemId);
  }

  public static PotionType fromIdx(int potionTypeId) {
    if (potionTypeId < 0 || potionTypeId >= TYPES.length) {
      return null;
    }
    return TYPES[potionTypeId];
  }
}
