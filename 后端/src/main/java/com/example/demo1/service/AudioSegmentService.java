package com.example.demo1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
public class AudioSegmentService {

    private static final Logger logger = LoggerFactory.getLogger(AudioSegmentService.class);
    private static final int MIN_SEGMENT = 20;
    private static final int MAX_SEGMENT = 60;

    private final BaiduSpeechService speech;
    private final ObjectMapper mapper;

    public AudioSegmentService(BaiduSpeechService speech, ObjectMapper mapper) {
        this.speech = speech;
        this.mapper = mapper;
    }

    public int findBreakPoint(String text) {
        int length = text.length();
        int sentenceSearchEnd = Math.min(length, MAX_SEGMENT + 10);
        for (int i = MIN_SEGMENT; i < sentenceSearchEnd; i++) {
            if (!isInsideCodeBlock(text, i) && isSentenceBreak(text.charAt(i))) {
                return i;
            }
        }

        if (length >= MAX_SEGMENT) {
            int fallbackStart = MAX_SEGMENT - 10;
            int fallbackEnd = Math.min(length, MAX_SEGMENT + 10);
            for (int i = fallbackStart; i < fallbackEnd; i++) {
                if (!isInsideCodeBlock(text, i) && isSoftBreak(text.charAt(i))) {
                    return i;
                }
            }
            return MAX_SEGMENT - 1;
        }
        return -1;
    }

    public String cleanForDisplay(String text) {
        return text
                .replaceAll("(?s)```.*?(?:```|$)", "")
                .replaceAll("`[^`]*`", "")
                .replaceAll("\\*\\*|__", "")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*>\\s?", "")
                .replaceAll("\\([^)]*\\)|[\\uFF08][^\\uFF09]*[\\uFF09]|\\[[^]]*\\]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\\n")
                .trim();
    }

    public String cleanForSpeech(String text) {
        return cleanForDisplay(text)
                .replaceAll("[\\u3010][^\\u3011]*[\\u3011]", "")
                .replaceAll("[\\*`_~#>\\[\\]()]", "")
                .replaceAll("[ \\t\\n]+", " ")
                .trim();
    }

    public void sendAudioSegment(String originalText, int index, SseEmitter emitter) {
        String dispText = cleanForDisplay(originalText);
        String cleanText = cleanForSpeech(originalText);
        if (cleanText.isEmpty()) {
            safeSend(emitter, "audio", Map.of("data", "", "text", dispText, "index", index));
            return;
        }

        try {
            String audioData = Base64.getEncoder().encodeToString(speech.synthesize(cleanText));
            safeSend(emitter, "audio", Map.of("data", audioData, "text", dispText, "index", index));
        } catch (IOException | IllegalStateException e) {
            logger.warn("Audio synthesis failed for segment {}", index, e);
            safeSend(emitter, "audio", Map.of("data", "", "text", dispText, "index", index));
        }
    }

    public void safeSend(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(mapper.writeValueAsString(data)));
        } catch (IllegalStateException | IOException e) {
            logger.debug("SSE client disconnected; skipped {} event", event);
        }
    }

    private boolean isSentenceBreak(char character) {
        return character == '\u3002' || character == '\uff01' || character == '\uff1f'
                || character == '\uff1b' || character == '\n';
    }

    private boolean isSoftBreak(char character) {
        return character == '\uff0c' || character == '\uff0e' || character == ',' || character == ' ';
    }

    private boolean isInsideCodeBlock(String text, int position) {
        int codeFenceCount = 0;
        int searchFrom = 0;
        while (true) {
            int fencePosition = text.indexOf("```", searchFrom);
            if (fencePosition < 0 || fencePosition >= position) {
                return codeFenceCount % 2 != 0;
            }
            codeFenceCount++;
            searchFrom = fencePosition + 3;
        }
    }
}
