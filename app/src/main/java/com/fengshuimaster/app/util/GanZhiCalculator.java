package com.fengshuimaster.app.util;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class GanZhiCalculator {

  private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
  private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

  /** 2000-01-01 為戊午日（六十甲子序號 54） */
  private static final LocalDate ANCHOR_DATE = LocalDate.of(2000, 1, 1);
  private static final int ANCHOR_INDEX = 54;

  private GanZhiCalculator() {}

  public static int getSexagenaryIndex(@NonNull LocalDate date) {
    long days = ChronoUnit.DAYS.between(ANCHOR_DATE, date);
    int index = (int) ((ANCHOR_INDEX + days) % 60);
    return index < 0 ? index + 60 : index;
  }

  @NonNull
  public static String getDayGanZhi(@NonNull LocalDate date) {
    int index = getSexagenaryIndex(date);
    return GAN[index % 10] + ZHI[index % 12];
  }

  public static int getDayGanIndex(@NonNull LocalDate date) {
    return getSexagenaryIndex(date) % 10;
  }

  @NonNull
  public static String getDayGan(@NonNull LocalDate date) {
    return GAN[getDayGanIndex(date)];
  }

  @NonNull
  public static String getDayZhi(@NonNull LocalDate date) {
    return ZHI[getSexagenaryIndex(date) % 12];
  }
}
