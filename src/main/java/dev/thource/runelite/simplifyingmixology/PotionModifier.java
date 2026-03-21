package dev.thource.runelite.simplifyingmixology;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PotionModifier {
  UNKNOWN("?"),
  HOMOGENOUS("H"),
  CONCENTRATED("Co"),
  CRYSTALISED("Cr");

  private static final PotionModifier[] TYPES = PotionModifier.values();

  public static PotionModifier fromText(String text) {
    if (text.contains("Homogenous")) {
      return HOMOGENOUS;
    }

    if (text.contains("Concentrated")) {
      return CONCENTRATED;
    }

    if (text.contains("Crystalised")) {
      return CRYSTALISED;
    }

    return UNKNOWN;
  }

  public static PotionModifier fromIndex(int index) {
    if (index < 0 || index >= TYPES.length) {
      return null;
    }

    return TYPES[index];
  }

  private final String shortText;
}
