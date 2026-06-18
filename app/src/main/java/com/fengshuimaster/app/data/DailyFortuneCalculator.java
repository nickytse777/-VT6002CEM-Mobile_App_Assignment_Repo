package com.fengshuimaster.app.data;

import com.fengshuimaster.app.model.DailyFortune;
import com.fengshuimaster.app.model.HourLuck;
import com.fengshuimaster.app.util.GanZhiCalculator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DailyFortuneCalculator {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.TRADITIONAL_CHINESE);

  private static final String[][] LUCKY_GODS = {
      {"東北方", "東北方", "東南方"}, // 甲
      {"西北方", "正東方", "西南方"}, // 乙
      {"西南方", "西南方", "西北方"}, // 丙
      {"正南方", "正南方", "東南方"}, // 丁
      {"東南方", "正北方", "東北方"}, // 戊
      {"東北方", "東北方", "東南方"}, // 己
      {"西北方", "正東方", "西南方"}, // 庚
      {"西南方", "西南方", "西北方"}, // 辛
      {"正南方", "正南方", "東南方"}, // 壬
      {"東南方", "正北方", "東北方"} // 癸
  };

  private static final String[] YANG_GUI = {
      "西南方", "正北方", "西北方", "正西方", "東北方",
      "正北方", "東北方", "正南方", "東南方", "正東方"
  };

  private static final String[][] TAI_SUI_BY_YEAR = {
      {"2025", "東南方", "西北方"}, // 乙巳 太岁东南
      {"2026", "正南方", "正北方"}, // 丙午
      {"2027", "西南方", "東北方"} // 丁未
  };

  private static final String[] HOUR_NAMES =
      {"子時", "丑時", "寅時", "卯時", "辰時", "巳時", "午時", "未時", "申時", "酉時", "戌時", "亥時"};
  private static final String[] HOUR_RANGES =
      {"23:00-01:00", "01:00-03:00", "03:00-05:00", "05:00-07:00",
          "07:00-09:00", "09:00-11:00", "11:00-13:00", "13:00-15:00",
          "15:00-17:00", "17:00-19:00", "19:00-21:00", "21:00-23:00"};
  private static final String[] HOUR_ZHI =
      {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

  private static final String[] YI_BY_GAN = {
      "祭祀、祈福、出行、會友",
      "交易、簽約、納財、求學",
      "修造、動土、開市、嫁娶",
      "求醫、療病、整頓、規劃",
      "置業、安床、納畜、修倉",
      "整理、歸庫、修飾、會晤",
      "裁衣、開渠、出行、立券",
      "修飾、清潔、會友、學藝",
      "納財、交易、出行、祈福",
      "修造、安機、納采、會親"
  };

  private static final String[] JI_BY_ZHI = {
      "忌動土、忌開倉",
      "忌嫁娶、忌出行",
      "忌祭祀、忌動土",
      "忌修造、忌開市",
      "忌破土、忌安葬",
      "忌出行、忌詞訟",
      "忌修造、忌動土",
      "忌嫁娶、忌移徙",
      "忌開市、忌立券",
      "忌安葬、忌修墳",
      "忌出行、忌赴任",
      "忌嫁娶、忌動土"
  };

  private DailyFortuneCalculator() {}

  public static DailyFortune buildForToday() {
    return buildForDate(LocalDate.now());
  }

  public static DailyFortune buildForDate(LocalDate date) {
    int ganIndex = GanZhiCalculator.getDayGanIndex(date);
    String dayZhi = GanZhiCalculator.getDayZhi(date);
    String ganZhi = GanZhiCalculator.getDayGanZhi(date) + "日";

    String[] taiSui = getTaiSuiForYear(date.getYear());
    List<HourLuck> hours = buildHourLuck(dayZhi, LocalTime.now());

    return new DailyFortune(
        date.format(DATE_FMT),
        ganZhi,
        LUCKY_GODS[ganIndex][0],
        LUCKY_GODS[ganIndex][1],
        LUCKY_GODS[ganIndex][2],
        YANG_GUI[ganIndex],
        taiSui[0],
        taiSui[1],
        YI_BY_GAN[ganIndex],
        JI_BY_ZHI[indexOfZhi(dayZhi)],
        hours);
  }

  private static String[] getTaiSuiForYear(int year) {
    for (String[] row : TAI_SUI_BY_YEAR) {
      if (Integer.parseInt(row[0]) == year) {
        return new String[] {row[1], row[2]};
      }
    }
    return new String[] {"正南方", "正北方"};
  }

  private static int indexOfZhi(String zhi) {
    for (int i = 0; i < HOUR_ZHI.length; i++) {
      if (HOUR_ZHI[i].equals(zhi)) {
        return i;
      }
    }
    return 0;
  }

  private static List<HourLuck> buildHourLuck(String dayZhi, LocalTime now) {
    int currentHourIndex = getCurrentHourIndex(now);
    List<HourLuck> result = new ArrayList<>(12);
    for (int i = 0; i < 12; i++) {
      HourLuck.Level level = getHourLevel(dayZhi, HOUR_ZHI[i]);
      result.add(new HourLuck(
          HOUR_NAMES[i],
          HOUR_RANGES[i],
          level,
          i == currentHourIndex));
    }
    return result;
  }

  private static int getCurrentHourIndex(LocalTime time) {
    int hour = time.getHour();
    if (hour >= 23 || hour < 1) return 0;
    if (hour < 3) return 1;
    if (hour < 5) return 2;
    if (hour < 7) return 3;
    if (hour < 9) return 4;
    if (hour < 11) return 5;
    if (hour < 13) return 6;
    if (hour < 15) return 7;
    if (hour < 17) return 8;
    if (hour < 19) return 9;
    if (hour < 21) return 10;
    return 11;
  }

  private static HourLuck.Level getHourLevel(String dayZhi, String hourZhi) {
    if (isLiuHe(dayZhi, hourZhi)) {
      return HourLuck.Level.GOOD;
    }
    if (isLiuChong(dayZhi, hourZhi)) {
      return HourLuck.Level.BAD;
    }
    return HourLuck.Level.NEUTRAL;
  }

  private static boolean isLiuHe(String a, String b) {
    return ("子".equals(a) && "丑".equals(b)) || ("丑".equals(a) && "子".equals(b))
        || ("寅".equals(a) && "亥".equals(b)) || ("亥".equals(a) && "寅".equals(b))
        || ("卯".equals(a) && "戌".equals(b)) || ("戌".equals(a) && "卯".equals(b))
        || ("辰".equals(a) && "酉".equals(b)) || ("酉".equals(a) && "辰".equals(b))
        || ("巳".equals(a) && "申".equals(b)) || ("申".equals(a) && "巳".equals(b))
        || ("午".equals(a) && "未".equals(b)) || ("未".equals(a) && "午".equals(b));
  }

  private static boolean isLiuChong(String a, String b) {
    return ("子".equals(a) && "午".equals(b)) || ("午".equals(a) && "子".equals(b))
        || ("丑".equals(a) && "未".equals(b)) || ("未".equals(a) && "丑".equals(b))
        || ("寅".equals(a) && "申".equals(b)) || ("申".equals(a) && "寅".equals(b))
        || ("卯".equals(a) && "酉".equals(b)) || ("酉".equals(a) && "卯".equals(b))
        || ("辰".equals(a) && "戌".equals(b)) || ("戌".equals(a) && "辰".equals(b))
        || ("巳".equals(a) && "亥".equals(b)) || ("亥".equals(a) && "巳".equals(b));
  }
}
