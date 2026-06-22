package com.fengshuimaster.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.gridlayout.widget.GridLayout;

import com.fengshuimaster.app.R;
import com.fengshuimaster.app.data.DailyFortuneCalculator;
import com.fengshuimaster.app.data.FlyingStarRepository;
import com.fengshuimaster.app.databinding.DialogFlyingStarDetailBinding;
import com.fengshuimaster.app.databinding.FragmentFengShuiBinding;
import com.fengshuimaster.app.model.DailyFortune;
import com.fengshuimaster.app.model.FlyingStarPalace;
import com.fengshuimaster.app.model.FlyingStarYear;
import com.fengshuimaster.app.model.HourLuck;
import com.fengshuimaster.app.util.CompassHelper;
import com.fengshuimaster.app.util.DailyFortuneUiHelper;
import com.fengshuimaster.app.util.FlyingStarTipsParser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

public class FengShuiFragment extends Fragment implements SensorEventListener {

    private static final int DEFAULT_YEAR = 2026;

    private static final int MODE_COMPASS = 0;
    private static final int MODE_FLYING_STAR = 1;
    private static final int MODE_DAILY = 2;

    private FragmentFengShuiBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private float lastAzimuth = -1f;
    private boolean hasAzimuth;
    private int selectedYear = DEFAULT_YEAR;
    private int currentMode = MODE_COMPASS;
    private DailyFortune dailyFortune;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFengShuiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        setupYearSelector();
        refreshFlyingStarUi();
        setupDailyFortuneUi();

        binding.toggleMode.check(R.id.btn_compass);
        showMode(MODE_COMPASS);

        binding.toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btn_flying_star) {
                showMode(MODE_FLYING_STAR);
            } else if (checkedId == R.id.btn_daily_lucky) {
                showMode(MODE_DAILY);
            } else {
                showMode(MODE_COMPASS);
            }
        });
    }

    private void setupYearSelector() {
        binding.toggleYear.check(R.id.btn_year_2026);
        binding.toggleYear.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btn_year_2025) {
                selectedYear = 2025;
            } else if (checkedId == R.id.btn_year_2027) {
                selectedYear = 2027;
            } else {
                selectedYear = 2026;
            }
            refreshFlyingStarUi();
        });
    }

    private void setupDailyFortuneUi() {
        dailyFortune = DailyFortuneCalculator.buildForToday();
        View dailyRoot = binding.layoutDailyContent.getRoot();

        TextView tvDate = dailyRoot.findViewById(R.id.tv_daily_date);
        TextView tvGanZhi = dailyRoot.findViewById(R.id.tv_daily_ganzhi);
        TextView tvTaiSui = dailyRoot.findViewById(R.id.tv_tai_sui);
        TextView tvUnlucky = dailyRoot.findViewById(R.id.tv_unlucky_dir);
        TextView tvYi = dailyRoot.findViewById(R.id.tv_daily_yi);
        TextView tvJi = dailyRoot.findViewById(R.id.tv_daily_ji);
        LinearLayout layoutHours = dailyRoot.findViewById(R.id.layout_hours);

        tvDate.setText(dailyFortune.dateLabel);
        tvGanZhi.setText(dailyFortune.ganZhi);
        tvTaiSui.setText(getString(R.string.daily_tai_sui_format, dailyFortune.taiSuiDirection));
        tvUnlucky.setText(getString(R.string.daily_unlucky_format, dailyFortune.unluckyDirection));
        tvYi.setText(getString(R.string.daily_yi_format, dailyFortune.yiSummary));
        tvJi.setText(getString(R.string.daily_ji_format, dailyFortune.jiSummary));

        bindDirectionCard(dailyRoot.findViewById(R.id.card_xi_shen), R.string.daily_label_xi_shen, dailyFortune.xiShen);
        bindDirectionCard(dailyRoot.findViewById(R.id.card_cai_shen), R.string.daily_label_cai_shen, dailyFortune.caiShen);
        bindDirectionCard(dailyRoot.findViewById(R.id.card_fu_shen), R.string.daily_label_fu_shen, dailyFortune.fuShen);
        bindDirectionCard(dailyRoot.findViewById(R.id.card_yang_gui), R.string.daily_label_yang_gui, dailyFortune.yangGui);

        layoutHours.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (HourLuck hour : dailyFortune.hours) {
            View item = inflater.inflate(R.layout.item_daily_hour, layoutHours, false);
            TextView tvName = item.findViewById(R.id.tv_hour_name);
            TextView tvRange = item.findViewById(R.id.tv_hour_range);
            TextView tvLuck = item.findViewById(R.id.tv_hour_luck);

            tvName.setText(hour.name);
            tvRange.setText(hour.timeRange);
            tvLuck.setText(getString(DailyFortuneUiHelper.hourLuckLabelRes(hour.level)));
            tvLuck.setTextColor(requireContext().getColor(DailyFortuneUiHelper.hourLuckColorRes(hour.level)));

            if (hour.current) {
                item.setBackgroundResource(R.drawable.bg_daily_compass_hint_good);
                tvName.setTextColor(requireContext().getColor(R.color.fs_primary_dark));
            }

            layoutHours.addView(item);
        }

        updateDailyCompassHint();
    }

    private void bindDirectionCard(@NonNull View card, int labelRes, @NonNull String direction) {
        TextView tvLabel = card.findViewById(R.id.tv_direction_label);
        TextView tvValue = card.findViewById(R.id.tv_direction_value);
        tvLabel.setText(labelRes);
        tvValue.setText(direction);
    }

    private void updateDailyCompassHint() {
        if (dailyFortune == null) {
            return;
        }
        View dailyRoot = binding.layoutDailyContent.getRoot();
        TextView tvHint = dailyRoot.findViewById(R.id.tv_compass_hint);
        View hintCard = dailyRoot.findViewById(R.id.layout_compass_hint);

        String hint = DailyFortuneUiHelper.buildCompassHint(
                requireContext(), dailyFortune, hasAzimuth, lastAzimuth);
        tvHint.setText(hint);

        boolean isGoodHint = hasAzimuth && (
                CompassHelper.isSameDirection(
                        CompassHelper.getBaguaDirection(lastAzimuth), dailyFortune.xiShen)
                        || CompassHelper.isSameDirection(
                        CompassHelper.getBaguaDirection(lastAzimuth), dailyFortune.caiShen));
        hintCard.setBackgroundResource(isGoodHint
                ? R.drawable.bg_daily_compass_hint_good
                : R.drawable.bg_info_chip);
    }

    private void refreshFlyingStarUi() {
        FlyingStarYear yearInfo = FlyingStarRepository.getYear(selectedYear);
        binding.tvChartTitle.setText(yearInfo.chartTitleRes);
        binding.tvCenterStarLabel.setText(yearInfo.centerStarLabelRes);
        setupFlyingStarGrid();
    }

    private void setupFlyingStarGrid() {
        GridLayout grid = binding.gridFlyingStar;
        grid.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        float density = getResources().getDisplayMetrics().density;
        int cellMargin = Math.round(5 * density);

        for (FlyingStarPalace palace : FlyingStarRepository.getPalaces(selectedYear)) {
            View cell = inflater.inflate(R.layout.item_flying_star_cell, grid, false);

            TextView tvDirection = cell.findViewById(R.id.tv_direction);
            TextView tvStarName = cell.findViewById(R.id.tv_star_name);
            TextView tvStarTitle = cell.findViewById(R.id.tv_star_title);

            int starColor = requireContext().getColor(
                    palace.isAuspicious() ? R.color.fs_star_auspicious : R.color.fs_star_inauspicious);
            cell.setBackgroundResource(
                    palace.isAuspicious()
                            ? R.drawable.bg_flying_star_cell_good
                            : R.drawable.bg_flying_star_cell_bad);

            tvDirection.setText(getString(palace.directionRes));
            tvStarName.setText(palace.formatStarName(requireContext()));
            tvStarName.setTextColor(starColor);
            tvStarTitle.setText(palace.formatStarLabel(requireContext()));
            tvStarTitle.setTextColor(starColor);

            cell.setOnClickListener(v -> showFlyingStarDetail(palace));

            GridLayout.Spec rowSpec = GridLayout.spec(palace.row, 1f);
            GridLayout.Spec colSpec = GridLayout.spec(palace.column, 1f);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(rowSpec, colSpec);
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin);
            cell.setLayoutParams(params);

            grid.addView(cell);
        }
    }

    private void showFlyingStarDetail(@NonNull FlyingStarPalace palace) {
        DialogFlyingStarDetailBinding dialogBinding =
                DialogFlyingStarDetailBinding.inflate(getLayoutInflater());

        boolean auspicious = palace.isAuspicious();
        int starColor = requireContext().getColor(
                auspicious ? R.color.fs_star_auspicious : R.color.fs_star_inauspicious);

        dialogBinding.layoutDetailHeader.setBackgroundResource(
                auspicious
                        ? R.drawable.bg_flying_star_dialog_header_good
                        : R.drawable.bg_flying_star_dialog_header_bad);
        dialogBinding.layoutStarBadge.setBackgroundResource(
                auspicious
                        ? R.drawable.bg_flying_star_star_badge_good
                        : R.drawable.bg_flying_star_star_badge_bad);
        dialogBinding.tvDetailNature.setBackgroundResource(
                auspicious
                        ? R.drawable.bg_flying_star_nature_good
                        : R.drawable.bg_flying_star_nature_bad);

        dialogBinding.tvDetailDirection.setText(getString(palace.directionRes));
        dialogBinding.tvDetailStar.setText(palace.formatStarName(requireContext()));
        dialogBinding.tvDetailStar.setTextColor(starColor);
        dialogBinding.tvDetailTitle.setText(palace.formatStarLabel(requireContext()));
        dialogBinding.tvDetailTitle.setTextColor(starColor);
        dialogBinding.tvDetailNature.setText(
                auspicious
                        ? getString(R.string.flying_star_nature_good)
                        : getString(R.string.flying_star_nature_bad));

        FlyingStarTipsParser.TipsPair tips =
                FlyingStarTipsParser.parse(palace.formatTips(requireContext()));
        dialogBinding.tvDetailTipsDo.setText(tips.doTips);
        if (tips.dontTips != null && !tips.dontTips.isEmpty()) {
            dialogBinding.layoutTipsDont.setVisibility(View.VISIBLE);
            dialogBinding.tvDetailTipsDont.setText(tips.dontTips);
        } else {
            dialogBinding.layoutTipsDont.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(
                        requireContext(), R.style.ThemeOverlay_FengShui_FlyingStarDetailDialog)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.flying_star_detail_close, null)
                .create();
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showMode(int mode) {
        currentMode = mode;
        binding.layoutCompass.setVisibility(mode == MODE_COMPASS ? View.VISIBLE : View.GONE);
        binding.layoutFlyingStar.setVisibility(mode == MODE_FLYING_STAR ? View.VISIBLE : View.GONE);
        binding.layoutDailyWrapper.setVisibility(mode == MODE_DAILY ? View.VISIBLE : View.GONE);
        if (mode == MODE_DAILY) {
            updateDailyCompassHint();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager == null) {
            return;
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, event.values.length);
        }

        float[] rotationMatrix = new float[9];
        float[] inclinationMatrix = new float[9];
        boolean success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic);
        if (!success) {
            return;
        }

        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        float azimuthInDegrees = CompassHelper.normalizeAzimuth((float) Math.toDegrees(orientation[0]));

        updateCompassUi(azimuthInDegrees);
    }

    private void updateCompassUi(float azimuthInDegrees) {
        lastAzimuth = azimuthInDegrees;
        hasAzimuth = true;

        if (currentMode == MODE_COMPASS) {
            binding.ivCompassFace.setRotation(-azimuthInDegrees);
            binding.tvDegree.setText(String.format(Locale.getDefault(), "%.0f°", azimuthInDegrees));
            binding.tvDirection.setText(CompassHelper.getBaguaDirection(azimuthInDegrees));
        }

        if (currentMode == MODE_DAILY) {
            updateDailyCompassHint();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op for initial release.
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
