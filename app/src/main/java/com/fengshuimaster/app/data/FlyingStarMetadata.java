package com.fengshuimaster.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.fengshuimaster.app.R;

public final class FlyingStarMetadata {

    public static final class StarInfo {
        @StringRes public final int nameRes;
        @StringRes public final int titleRes;
        @StringRes public final int tipsRes;
        public final boolean auspicious;

        StarInfo(@StringRes int nameRes, @StringRes int titleRes, @StringRes int tipsRes, boolean auspicious) {
            this.nameRes = nameRes;
            this.titleRes = titleRes;
            this.tipsRes = tipsRes;
            this.auspicious = auspicious;
        }
    }

    private static final StarInfo[] STARS = new StarInfo[10];

    static {
        STARS[1] = new StarInfo(R.string.fs_star1_name, R.string.fs_star1_title, R.string.fs_star1_tips, true);
        STARS[2] = new StarInfo(R.string.fs_star2_name, R.string.fs_star2_title, R.string.fs_star2_tips, false);
        STARS[3] = new StarInfo(R.string.fs_star3_name, R.string.fs_star3_title, R.string.fs_star3_tips, false);
        STARS[4] = new StarInfo(R.string.fs_star4_name, R.string.fs_star4_title, R.string.fs_star4_tips, true);
        STARS[5] = new StarInfo(R.string.fs_star5_name, R.string.fs_star5_title, R.string.fs_star5_tips, false);
        STARS[6] = new StarInfo(R.string.fs_star6_name, R.string.fs_star6_title, R.string.fs_star6_tips, true);
        STARS[7] = new StarInfo(R.string.fs_star7_name, R.string.fs_star7_title, R.string.fs_star7_tips, false);
        STARS[8] = new StarInfo(R.string.fs_star8_name, R.string.fs_star8_title, R.string.fs_star8_tips, true);
        STARS[9] = new StarInfo(R.string.fs_star9_name, R.string.fs_star9_title, R.string.fs_star9_tips, true);
    }

    private FlyingStarMetadata() {}

    @NonNull
    public static StarInfo requireStar(int starNumber) {
        if (starNumber < 1 || starNumber > 9) {
            throw new IllegalArgumentException("Invalid star number: " + starNumber);
        }
        return STARS[starNumber];
    }
}
