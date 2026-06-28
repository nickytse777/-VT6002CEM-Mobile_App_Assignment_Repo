package com.fengshuimaster.app.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.fengshuimaster.app.R;
import com.fengshuimaster.app.model.DailyFortune;

public final class DailyFortuneUiHelper {

    private static final float NEAR_DIRECTION_THRESHOLD = 22.5f;

    private DailyFortuneUiHelper() {}

    @NonNull
    public static String buildCompassHint(
            @NonNull Context context,
            @NonNull DailyFortune fortune,
            boolean hasAzimuth,
            float azimuthDegrees) {
        if (!hasAzimuth) {
            return context.getString(R.string.daily_compass_waiting);
        }

        String facing = CompassHelper.getBaguaDirection(azimuthDegrees);
        if (CompassHelper.isSameDirection(facing, fortune.xiShen)) {
            return context.getString(R.string.daily_compass_match_xi, facing);
        }
        if (isNearDirection(azimuthDegrees, fortune.xiShen)) {
            return context.getString(R.string.daily_compass_near_xi, facing, fortune.xiShen);
        }
        if (CompassHelper.isSameDirection(facing, fortune.caiShen)) {
            return context.getString(R.string.daily_compass_match_cai, facing);
        }
        if (CompassHelper.isSameDirection(facing, fortune.unluckyDirection)
                || isNearDirection(azimuthDegrees, fortune.unluckyDirection)) {
            return context.getString(R.string.daily_compass_match_bad, facing, fortune.unluckyDirection);
        }
        return context.getString(
                R.string.daily_compass_general, facing, fortune.xiShen, fortune.caiShen);
    }

    private static boolean isNearDirection(float azimuthDegrees, @NonNull String targetDirection) {
        float targetAzimuth = CompassHelper.directionToAzimuth(targetDirection);
        if (targetAzimuth < 0f) {
            return false;
        }
        return CompassHelper.smallestAngleDifference(azimuthDegrees, targetAzimuth) <= NEAR_DIRECTION_THRESHOLD;
    }

    @StringRes
    public static int hourLuckLabelRes(@NonNull com.fengshuimaster.app.model.HourLuck.Level level) {
        switch (level) {
            case GOOD:
                return R.string.daily_hour_good;
            case BAD:
                return R.string.daily_hour_bad;
            default:
                return R.string.daily_hour_neutral;
        }
    }

    public static int hourLuckColorRes(@NonNull com.fengshuimaster.app.model.HourLuck.Level level) {
        switch (level) {
            case GOOD:
                return R.color.fs_star_auspicious;
            case BAD:
                return R.color.fs_star_inauspicious;
            default:
                return R.color.fs_text_secondary;
        }
    }
}
