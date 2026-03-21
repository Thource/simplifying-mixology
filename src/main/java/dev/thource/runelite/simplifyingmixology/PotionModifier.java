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

  private final String shortText;
}
