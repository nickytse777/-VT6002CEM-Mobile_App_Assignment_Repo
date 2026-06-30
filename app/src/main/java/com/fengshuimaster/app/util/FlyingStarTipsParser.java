package com.fengshuimaster.app.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class FlyingStarTipsParser {

  private FlyingStarTipsParser() {}

  public static final class TipsPair {
    @NonNull public final String doTips;
    @Nullable public final String dontTips;

    public TipsPair(@NonNull String doTips, @Nullable String dontTips) {
      this.doTips = doTips;
      this.dontTips = dontTips;
    }
  }

  @NonNull
  public static TipsPair parse(@NonNull String tips) {
    String trimmed = tips.trim();
    if (!trimmed.startsWith("宜")) {
      return new TipsPair(trimmed, null);
    }

    int dontIndex = trimmed.indexOf("；忌");
    if (dontIndex < 0) {
      return new TipsPair(stripPrefix(trimmed, "宜"), null);
    }

    String doPart = stripPrefix(trimmed.substring(0, dontIndex), "宜");
    String dontPart = stripPrefix(trimmed.substring(dontIndex + 2), "忌");
    return new TipsPair(doPart, dontPart.isEmpty() ? null : dontPart);
  }

  @NonNull
  private static String stripPrefix(@NonNull String value, @NonNull String prefix) {
    String result = value.trim();
    if (result.startsWith(prefix)) {
      result = result.substring(prefix.length());
    }
    if (result.startsWith("：") || result.startsWith(":")) {
      result = result.substring(1);
    }
    return result.trim();
  }
}
