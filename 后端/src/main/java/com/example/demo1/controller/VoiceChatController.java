package com.example.demo1.controller;

import com.example.demo1.service.AudioSegmentService;
import com.example.demo1.service.BaiduSpeechService;
import com.example.demo1.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:5173}")
public class VoiceChatController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceChatController.class);

    private final BaiduSpeechService speech;
    private final ChatService chatService;
    private final AudioSegmentService segmentService;
    private final ObjectMapper mapper;

    public VoiceChatController(BaiduSpeechService speech, ChatService chatService,
                               AudioSegmentService segmentService, ObjectMapper mapper) {
        this.speech = speech;
        this.chatService = chatService;
        this.segmentService = segmentService;
        this.mapper = mapper;
    }

    @PostMapping(value = "/api/voice-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter voiceChat(@RequestParam("audio") MultipartFile audio) {
        SseEmitter emitter = new SseEmitter(120000L);

        new Thread(() -> {
            try {
                String asrJson = speech.recognize(audio.getBytes(), "wav");
                String userText = extractText(asrJson);
                if (userText.isEmpty()) {
                    segmentService.safeSend(emitter, "userText", Map.of("text", ""));
                    segmentService.safeSend(emitter, "error", Map.of("message", "Speech was not recognized"));
                    segmentService.safeSend(emitter, "done", Map.of());
                    emitter.complete();
                    return;
                }

                segmentService.safeSend(emitter, "userText", Map.of("text", userText));
                runChatLoop(userText, emitter);
                segmentService.safeSend(emitter, "done", Map.of());
                emitter.complete();
            } catch (Exception e) {
                logger.error("Voice chat processing failed", e);
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                segmentService.safeSend(emitter, "error", Map.of("message", message));
                emitter.complete();
            }
        }, "voice-chat-worker").start();

        return emitter;
    }

    private void runChatLoop(String userText, SseEmitter emitter) {
        StringBuilder buffer = new StringBuilder();
        int[] segmentIndex = {0};

        chatService.chatStream(userText)
                .doOnNext(chunk -> {
                    buffer.append(chunk);
                    int breakPoint;
                    while ((breakPoint = segmentService.findBreakPoint(buffer.toString())) >= 0) {
                        String segment = buffer.substring(0, breakPoint + 1);
                        buffer.delete(0, breakPoint + 1);
                        segmentService.sendAudioSegment(segment, segmentIndex[0]++, emitter);
                    }
                })
                .doOnComplete(() -> {
                    String remainingText = buffer.toString().trim();
                    if (!remainingText.isEmpty()) {
                        segmentService.sendAudioSegment(remainingText, segmentIndex[0]++, emitter);
                    }
                    if (segmentIndex[0] == 0) {
                        segmentService.safeSend(emitter, "error", Map.of("message", "AI response is empty"));
                    }
                })
                .doOnError(error -> logger.error("AI stream failed", error))
                .blockLast();
    }

    private String extractText(String asrJson) throws java.io.IOException {
        JsonNode root = mapper.readTree(asrJson);
        if (root.path("err_no").asInt(-1) != 0) {
            String message = root.path("err_msg").asText("Speech recognition failed");
            throw new java.io.IOException(message);
        }
        JsonNode result = root.path("result");
        if (!result.isArray() || result.isEmpty()) {
            return "";
        }
        return result.get(0).asText("");
    }
}
