package com.example.demo1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class BaiduSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(BaiduSpeechService.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    @Value("${baidu.api-key}")
    private String apiKey;

    @Value("${baidu.secret-key}")
    private String secretKey;

    @Value("${baidu.asr-url}")
    private String asrUrl;

    @Value("${baidu.tts-url}")
    private String ttsUrl;

    @Value("${baidu.tts-voice-id}")
    private int ttsVoiceId;

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public BaiduSpeechService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private String getAccessToken() {
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey
                + "&client_secret=" + secretKey;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0], null))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                logger.error("获取百度语音访问令牌失败，状态码：{}，响应：{}", response.code(), responseBody);
                throw new IllegalStateException("获取百度语音访问令牌失败");
            }

            JsonNode json = objectMapper.readTree(responseBody);
            String accessToken = json.path("access_token").asText();
            if (accessToken.isBlank()) {
                logger.error("百度语音令牌响应中缺少 access_token，响应：{}", responseBody);
                throw new IllegalStateException("百度语音令牌响应格式异常");
            }
            return accessToken;
        } catch (IOException e) {
            logger.error("请求百度语音访问令牌时发生网络异常", e);
            throw new IllegalStateException("请求百度语音访问令牌失败", e);
        }
    }

    public String recognize(byte[] audio, String format) throws IOException {
        String accessToken = getAccessToken();
        String base64Audio = Base64.getEncoder().encodeToString(audio);
        Map<String, Object> requestData = Map.of(
                "format", format,
                "rate", 16000,
                "channel", 1,
                "dev_pid", 1537,
                "token", accessToken,
                "cuid", "cyberpet",
                "len", audio.length,
                "speech", base64Audio
        );
        String requestJson = objectMapper.writeValueAsString(requestData);

        Request request = new Request.Builder()
                .url(asrUrl)
                .post(RequestBody.create(requestJson, MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body() == null ? "" : response.body().string();
        }
    }

    public byte[] synthesize(String text) throws IOException {
        String accessToken = getAccessToken();
        Map<String, Object> requestData = Map.of(
                "text", text,
                "voice_id", ttsVoiceId,
                "media_type", "mp3"
        );
        String requestJson = objectMapper.writeValueAsString(requestData);
        Request request = new Request.Builder()
                .url(ttsUrl + "?access_token=" + accessToken)
                .post(RequestBody.create(requestJson, MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String contentType = response.header("Content-Type", "");
            if (contentType.startsWith("audio")) {
                return response.body() == null ? new byte[0] : response.body().bytes();
            }
            String error = response.body() == null ? "" : response.body().string();
            throw new IOException("TTS failed: " + error);
        }
    }
}
