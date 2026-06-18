package com.fengshuimaster.app.data;

import com.fengshuimaster.app.R;
import com.fengshuimaster.app.model.FlyingStarPalace;

import java.util.ArrayList;
import java.util.List;

/**
 * 依流年入中星順飛排列九宮（上南下北、左東右西）。
 * 飛星路線：中宮 → 西北 → 正西 → 東北 → 正南 → 正北 → 西南 → 正東 → 東南。
 */
public final class FlyingStarCalculator {

    private static final int[][] FLY_PATH = {
            {1, 1, R.string.fs_c_direction},
            {2, 2, R.string.fs_nw_direction},
            {1, 2, R.string.fs_w_direction},
            {2, 0, R.string.fs_ne_direction},
            {0, 1, R.string.fs_s_direction},
            {2, 1, R.string.fs_n_direction},
            {0, 2, R.string.fs_sw_direction},
            {1, 0, R.string.fs_e_direction},
            {0, 0, R.string.fs_se_direction}
    };

    private FlyingStarCalculator() {}

    public static List<FlyingStarPalace> buildPalaces(int centerStar) {
        List<FlyingStarPalace> palaces = new ArrayList<>(9);
        for (int i = 0; i < FLY_PATH.length; i++) {
            int starNumber = normalizeStar(centerStar + i);
            int row = FLY_PATH[i][0];
            int col = FLY_PATH[i][1];
            int directionRes = FLY_PATH[i][2];
            palaces.add(new FlyingStarPalace(directionRes, starNumber, row, col));
        }
        return palaces;
    }

    private static int normalizeStar(int value) {
        int star = value % 9;
        return star == 0 ? 9 : star;
    }
}
