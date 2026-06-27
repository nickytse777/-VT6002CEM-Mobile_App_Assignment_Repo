package com.fengshuimaster.app.util;

import androidx.annotation.NonNull;

public final class CompassHelper {

    public static final String[] MOUNTAINS_24 = {
            "子", "癸", "丑", "艮", "寅", "甲", "卯", "乙",
            "辰", "巽", "巳", "丙", "午", "丁", "未", "坤",
            "申", "庚", "酉", "辛", "戌", "乾", "亥", "壬"
    };

    private static final String[] DIRECTIONS_8 = {
            "北", "東北", "東", "東南", "南", "西南", "西", "西北"
    };

    private CompassHelper() {}

    public static float normalizeAzimuth(float degrees) {
        float normalized = degrees % 360f;
        return normalized < 0f ? normalized + 360f : normalized;
    }

    @NonNull
    public static String getMountain(float azimuthDegrees) {
        float normalized = normalizeAzimuth(azimuthDegrees);
        int index = (int) ((normalized + 7.5f) / 15f) % 24;
        return MOUNTAINS_24[index];
    }

    @NonNull
    public static String getBaguaDirection(float azimuthDegrees) {
        float normalized = normalizeAzimuth(azimuthDegrees);
        if (normalized >= 337.5f || normalized < 22.5f) return DIRECTIONS_8[0];
        if (normalized < 67.5f) return DIRECTIONS_8[1];
        if (normalized < 112.5f) return DIRECTIONS_8[2];
        if (normalized < 157.5f) return DIRECTIONS_8[3];
        if (normalized < 202.5f) return DIRECTIONS_8[4];
        if (normalized < 247.5f) return DIRECTIONS_8[5];
        if (normalized < 292.5f) return DIRECTIONS_8[6];
        return DIRECTIONS_8[7];
    }

    @NonNull
    public static String formatSittingFacing(float azimuthDegrees) {
        float facing = normalizeAzimuth(azimuthDegrees);
        float sitting = normalizeAzimuth(facing + 180f);
        return getMountain(sitting) + "山 · " + getMountain(facing) + "向";
    }

    @NonNull
    public static String normalizeDirectionLabel(@NonNull String direction) {
        return direction
                .replace("正", "")
                .replace("方", "")
                .trim();
    }

    public static boolean isSameDirection(@NonNull String facingDirection, @NonNull String targetDirection) {
        String facing = normalizeDirectionLabel(facingDirection);
        String target = normalizeDirectionLabel(targetDirection);
        return facing.equals(target);
    }

    public static float directionToAzimuth(@NonNull String direction) {
        String normalized = normalizeDirectionLabel(direction);
        switch (normalized) {
            case "北":
                return 0f;
            case "東北":
                return 45f;
            case "東":
                return 90f;
            case "東南":
                return 135f;
            case "南":
                return 180f;
            case "西南":
                return 225f;
            case "西":
                return 270f;
            case "西北":
                return 315f;
            default:
                return -1f;
        }
    }

    public static float smallestAngleDifference(float azimuth, float targetAzimuth) {
        float diff = normalizeAzimuth(azimuth) - normalizeAzimuth(targetAzimuth);
        while (diff > 180f) {
            diff -= 360f;
        }
        while (diff < -180f) {
            diff += 360f;
        }
        return Math.abs(diff);
    }
}
