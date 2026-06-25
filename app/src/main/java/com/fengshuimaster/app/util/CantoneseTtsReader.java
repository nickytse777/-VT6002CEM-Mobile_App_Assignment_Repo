package com.fengshuimaster.app.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads text aloud in Cantonese using the system Text-to-Speech engine.
 */
public final class CantoneseTtsReader implements TextToSpeech.OnInitListener {

    public interface Listener {
        void onReady();

        void onSpeakStart();

        void onSpeakDone();

        void onError(@NonNull String message);
    }

    private static final Locale[] CANTONESE_LOCALES = {
            Locale.forLanguageTag("yue-HK"),
            new Locale("zh", "HK"),
            Locale.forLanguageTag("zh-HK")
    };

    private static final int MAX_CHUNK_LENGTH = 3200;

    private final Context appContext;
    @Nullable
    private final Listener listener;

    @Nullable
    private TextToSpeech tts;
    private boolean ready;
    private boolean speaking;
    @Nullable
    private Locale appliedLocale;

    public CantoneseTtsReader(@NonNull Context context, @Nullable Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.tts = new TextToSpeech(appContext, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            notifyError("語音引擎初始化失敗。");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
        }

        appliedLocale = resolveCantoneseLocale(tts);
        if (appliedLocale == null) {
            notifyError("此手機未支援廣東話語音，請到系統設定下載語音資料。");
            return;
        }

        int langResult = tts.setLanguage(appliedLocale);
        if (langResult == TextToSpeech.LANG_MISSING_DATA
                || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            notifyError("廣東話語音資料未安裝，請到系統設定下載。");
            return;
        }

        tts.setSpeechRate(0.92f);
        tts.setPitch(1.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    speaking = true;
                    if (listener != null) {
                        listener.onSpeakStart();
                    }
                }

                @Override
                public void onDone(String utteranceId) {
                    if ("chunk-final".equals(utteranceId)) {
                        speaking = false;
                        if (listener != null) {
                            listener.onSpeakDone();
                        }
                    }
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onError(String utteranceId) {
                    speaking = false;
                    notifyError("語音播放失敗。");
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    speaking = false;
                    notifyError("語音播放失敗。");
                }
            });
        }

        ready = true;
        if (listener != null) {
            listener.onReady();
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void speak(@NonNull String text) {
        if (!ready || tts == null) {
            notifyError("語音引擎尚未準備好，請稍後再試。");
            return;
        }

        String prepared = prepareForSpeech(text);
        if (prepared.isEmpty()) {
            notifyError("沒有可解讀的內容。");
            return;
        }

        tts.stop();
        List<String> chunks = splitIntoChunks(prepared);
        for (int i = 0; i < chunks.size(); i++) {
            String utteranceId = i == chunks.size() - 1 ? "chunk-final" : "chunk-" + i;
            int queueMode = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                tts.speak(chunks.get(i), queueMode, params, utteranceId);
            } else {
                tts.speak(chunks.get(i), queueMode, null);
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            speaking = true;
            if (listener != null) {
                listener.onSpeakStart();
            }
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
        speaking = false;
        if (listener != null) {
            listener.onSpeakDone();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
        speaking = false;
    }

    @NonNull
    public static String prepareForSpeech(@NonNull String raw) {
        String text = raw
                .replaceAll("[*#_>`]", "")
                .replaceAll("\\r\\n|\\r|\\n", "。")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("。{2,}", "。")
                .trim();
        if (!text.endsWith("。") && !text.endsWith("！") && !text.endsWith("？")) {
            text = text + "。";
        }
        return text;
    }

    @NonNull
    private static List<String> splitIntoChunks(@NonNull String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= MAX_CHUNK_LENGTH) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, text.length());
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('。', end);
                if (breakAt > start + 200) {
                    end = breakAt + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    @Nullable
    private static Locale resolveCantoneseLocale(@NonNull TextToSpeech engine) {
        for (Locale locale : CANTONESE_LOCALES) {
            int result = engine.isLanguageAvailable(locale);
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                return locale;
            }
        }
        return null;
    }

    private void notifyError(@NonNull String message) {
        speaking = false;
        if (listener != null) {
            listener.onError(message);
        }
    }
}
