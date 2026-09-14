package com.example.demo1.service;


import com.example.demo1.tool.FeishuTool;
import com.example.demo1.tool.FileTools;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import org.slf4j.Logger;

import java.util.regex.Pattern;

@Component
@Service
public class ChatService {


    //日志
    Logger logger = (Logger) LoggerFactory.getLogger(ChatService.class);
    //创建ai客户端

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    //添加告别词,清空记忆
    private static final Pattern GOODBYE=Pattern.compile((".*(再见|拜拜|退下).*"));

    //初始化构造,添加操作工具(飞书)
    public ChatService(ChatClient.Builder builder, @Value("${bailian.system-prompt}") String systemPrompt, FileTools fileTools, FeishuTool feishuTool){
        //创建滑动窗口记忆

        this.chatMemory= MessageWindowChatMemory.builder()
                .maxMessages(8)
                .build();
        this.chatClient=builder
                .defaultSystem(systemPrompt)
                .defaultTools(fileTools,feishuTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(chatMemory.DEFAULT_CONVERSATION_ID).build())
                .build();

    }
    public Flux<String> chatStream(String userMessage){
        if(GOODBYE.matcher(userMessage).matches()){
            logger.info("检测到告别词,清空对话记忆",userMessage);
            chatMemory.clear(ChatMemory.DEFAULT_CONVERSATION_ID);
        }
        return chatClient.prompt()
                .user(userMessage)
                .stream()
                .content()
                .doOnError((Throwable e) -> logger.error("AI请求异常",e));

    }
}
