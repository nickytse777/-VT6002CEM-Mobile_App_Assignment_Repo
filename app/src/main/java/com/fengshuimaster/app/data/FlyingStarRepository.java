package com.fengshuimaster.app.data;

import com.fengshuimaster.app.R;
import com.fengshuimaster.app.model.FlyingStarPalace;
import com.fengshuimaster.app.model.FlyingStarYear;

import java.util.Arrays;
import java.util.List;

public final class FlyingStarRepository {

    private static final List<FlyingStarYear> YEARS = Arrays.asList(
            new FlyingStarYear(2025, 2, R.string.fs_chart_title_2025, R.string.fs_center_star_2025),
            new FlyingStarYear(2026, 1, R.string.fs_chart_title_2026, R.string.fs_center_star_2026),
            new FlyingStarYear(2027, 9, R.string.fs_chart_title_2027, R.string.fs_center_star_2027)
    );

    private FlyingStarRepository() {}

    public static List<FlyingStarYear> getYears() {
        return YEARS;
    }

    public static FlyingStarYear getYear(int year) {
        for (FlyingStarYear item : YEARS) {
            if (item.year == year) {
                return item;
            }
        }
        return YEARS.get(1);
    }

    public static List<FlyingStarPalace> getPalaces(int year) {
        return FlyingStarCalculator.buildPalaces(getYear(year).centerStar);
    }
}
