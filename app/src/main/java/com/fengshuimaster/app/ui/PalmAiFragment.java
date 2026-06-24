package com.fengshuimaster.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.fengshuimaster.app.R;
import com.fengshuimaster.app.data.OpenRouterClient;
import com.fengshuimaster.app.databinding.FragmentPalmAiBinding;
import com.fengshuimaster.app.model.PalmResultSection;
import com.fengshuimaster.app.util.PalmImageUtil;
import com.fengshuimaster.app.util.PalmPdfExporter;
import com.fengshuimaster.app.util.PalmResultParser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class PalmAiFragment extends Fragment {

    private FragmentPalmAiBinding binding;
    private Bitmap selectedBitmap;
    private String lastSuccessfulReport;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable elapsedTicker;
    private long analysisStartMs;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCameraPreview();
                } else {
                    Toast.makeText(requireContext(), "未授權相機，請改用相簿選圖。", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Void> cameraCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap == null) {
                    return;
                }
                selectedBitmap = bitmap;
                binding.ivPalmPreview.setImageBitmap(bitmap);
                ImageViewCompat.setImageTintList(binding.ivPalmPreview, null);
                clearResultUi();
            });

    private final ActivityResultLauncher<String> galleryPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleGalleryImage);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPalmAiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnOpenCamera.setOnClickListener(v -> openImageSource());
        binding.btnStartAnalysis.setOnClickListener(v -> promptHandSideThenAnalyze());
        binding.btnSavePdf.setOnClickListener(v -> saveReportPdf());
    }

    private void openImageSource() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("選擇圖片來源")
                .setItems(new String[]{"相簿選圖", "拍照"}, (dialog, which) -> {
                    if (which == 0) {
                        galleryPickerLauncher.launch("image/*");
                    } else {
                        requestCameraIfNeededThenCapture();
                    }
                })
                .show();
    }

    private void requestCameraIfNeededThenCapture() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCameraPreview();
            return;
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCameraPreview() {
        cameraCaptureLauncher.launch(null);
    }

    private void handleGalleryImage(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                Toast.makeText(requireContext(), "讀取圖片失敗。", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(requireContext(), "圖片格式不支援。", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedBitmap = bitmap;
            binding.ivPalmPreview.setImageBitmap(bitmap);
            ImageViewCompat.setImageTintList(binding.ivPalmPreview, null);
            clearResultUi();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "讀取圖片失敗：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void promptHandSideThenAnalyze() {
        if (selectedBitmap == null) {
            Toast.makeText(requireContext(), "請先拍攝或選擇手掌圖片。", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_select_hand)
                .setItems(new String[]{"左手", "右手"}, (dialog, which) -> {
                    String handSide = which == 0 ? "左手" : "右手";
                    startAnalysis(handSide);
                })
                .show();
    }

    private void startAnalysis(@NonNull String handSide) {
        setAnalysisUiActive(true);
        showPreparingState();

        final Bitmap bitmap = selectedBitmap;
        new Thread(() -> {
            String base64Image = PalmImageUtil.toBase64Jpeg(bitmap);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                beginAnalyzingState();
                OpenRouterClient client = new OpenRouterClient();
                client.analyzePalmImage(base64Image, handSide, new OpenRouterClient.PalmCallback() {
                    @Override
                    public void onSuccess(String content) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() ->
                                handleAnalysisSuccess(handSide, content));
                    }

                    @Override
                    public void onFailure(String message) {
                        if (!isAdded()) {
                            return;
                        }
                        requireActivity().runOnUiThread(() ->
                                handleAnalysisFailure(message));
                    }
                });
            });
        }).start();
    }

    private void showPreparingState() {
        binding.tvAnalysisStatus.setVisibility(View.VISIBLE);
        binding.tvAnalysisHint.setVisibility(View.GONE);
        binding.tvAnalysisStatus.setText(R.string.palm_preparing_image);
        binding.tvResultPlaceholder.setVisibility(View.VISIBLE);
        binding.tvResultPlaceholder.setText(R.string.palm_preparing_image);
        binding.layoutResultSections.setVisibility(View.GONE);
        binding.layoutResultSections.removeAllViews();
    }

    private void beginAnalyzingState() {
        binding.tvAnalysisStatus.setText(R.string.palm_analyzing);
        binding.tvAnalysisHint.setVisibility(View.VISIBLE);
        binding.tvResultPlaceholder.setText(R.string.palm_analyzing);
        startElapsedTimer();
    }

    private void startElapsedTimer() {
        stopElapsedTimer();
        analysisStartMs = System.currentTimeMillis();
        elapsedTicker = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || binding == null) {
                    return;
                }
                int elapsedSec = (int) ((System.currentTimeMillis() - analysisStartMs) / 1000L);
                binding.tvAnalysisStatus.setText(
                        getString(R.string.palm_analyzing_elapsed, elapsedSec));
                mainHandler.postDelayed(this, 1000L);
            }
        };
        mainHandler.post(elapsedTicker);
    }

    private void stopElapsedTimer() {
        if (elapsedTicker != null) {
            mainHandler.removeCallbacks(elapsedTicker);
            elapsedTicker = null;
        }
    }

    private void handleAnalysisSuccess(@NonNull String handSide, @NonNull String content) {
        stopElapsedTimer();
        setAnalysisUiActive(false);
        lastSuccessfulReport = content;
        binding.btnSavePdf.setEnabled(true);
        renderResultSections(handSide, content);
    }

    private void handleAnalysisFailure(@NonNull String message) {
        stopElapsedTimer();
        setAnalysisUiActive(false);
        lastSuccessfulReport = null;
        binding.btnSavePdf.setEnabled(false);
        binding.tvAnalysisStatus.setVisibility(View.GONE);
        binding.tvAnalysisHint.setVisibility(View.GONE);
        binding.layoutResultSections.setVisibility(View.GONE);
        binding.layoutResultSections.removeAllViews();
        binding.tvResultPlaceholder.setVisibility(View.VISIBLE);
        binding.tvResultPlaceholder.setText("分析失敗：" + message);
    }

    private void renderResultSections(@NonNull String handSide, @NonNull String content) {
        binding.tvAnalysisStatus.setVisibility(View.GONE);
        binding.tvAnalysisHint.setVisibility(View.GONE);
        binding.tvResultPlaceholder.setVisibility(View.GONE);
        binding.layoutResultSections.setVisibility(View.VISIBLE);
        binding.layoutResultSections.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        View handCard = inflater.inflate(R.layout.item_palm_result_section, binding.layoutResultSections, false);
        TextView handTitle = handCard.findViewById(R.id.tv_section_title);
        TextView handContent = handCard.findViewById(R.id.tv_section_content);
        handTitle.setText(getString(R.string.palm_result_hand_label, handSide));
        handContent.setText(R.string.palm_result_intro);
        binding.layoutResultSections.addView(handCard);

        List<PalmResultSection> sections = PalmResultParser.parse(content);
        for (PalmResultSection section : sections) {
            View card = inflater.inflate(R.layout.item_palm_result_section, binding.layoutResultSections, false);
            TextView tvTitle = card.findViewById(R.id.tv_section_title);
            TextView tvContent = card.findViewById(R.id.tv_section_content);
            tvTitle.setText(section.title);
            tvContent.setText(section.content);
            binding.layoutResultSections.addView(card);
        }
    }

    private void clearResultUi() {
        stopElapsedTimer();
        lastSuccessfulReport = null;
        binding.btnSavePdf.setEnabled(false);
        binding.tvAnalysisStatus.setVisibility(View.GONE);
        binding.tvAnalysisHint.setVisibility(View.GONE);
        binding.layoutResultSections.setVisibility(View.GONE);
        binding.layoutResultSections.removeAllViews();
        binding.tvResultPlaceholder.setVisibility(View.VISIBLE);
        binding.tvResultPlaceholder.setText(R.string.analysis_placeholder);
    }

    private void setAnalysisUiActive(boolean active) {
        binding.progressAnalysis.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.btnStartAnalysis.setEnabled(!active);
        binding.btnOpenCamera.setEnabled(!active);
        if (!active) {
            binding.tvAnalysisHint.setVisibility(View.GONE);
        }
    }

    private void saveReportPdf() {
        if (lastSuccessfulReport == null || lastSuccessfulReport.trim().isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.analysis_placeholder), Toast.LENGTH_SHORT).show();
            return;
        }
        binding.btnSavePdf.setEnabled(false);
        final Context appCtx = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                File file = PalmPdfExporter.exportReport(appCtx, lastSuccessfulReport);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    binding.btnSavePdf.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.toast_pdf_saved, file.getName()),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    binding.btnSavePdf.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.toast_pdf_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        stopElapsedTimer();
        super.onDestroyView();
        binding = null;
    }
}
