package com.example.demo1.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;


@Component
public class FeishuTool {
    //在飞书中创建群聊机器人,获得webhook
    private final String webhookurl;
    public FeishuTool(@Value("${feishu.webhook-url}")String webhookurl){
        this.webhookurl=webhookurl;
    }
    //日志工具
    private static final Logger logger= LoggerFactory.getLogger(FeishuTool.class);
    private final RestTemplate restTemplate=new RestTemplate();

    //书写工具 飞书推送
    @Tool(description = "发送一条文本信息到飞书群,当用户说到:通知XX,发到群里,飞书提醒的时候调用")
    public String sendFeishuMessage(
            @ToolParam(description ="要发送的消息,纯文本")String content
    ){
        //1防御性检查:如果webhook没有正确配置,就选择不执行
        if(webhookurl==null||webhookurl.isEmpty()){
            return "当前飞书webhook未配置,请配置后再次尝试";
        }
        //构建请求头
        HttpHeaders headers=new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        //构建请求体
        Map<String,Object> body=Map.of(
                "msg_type","text",
                "content",Map.of("text",content)
        );
        HttpEntity<Map<String,Object>> req=new HttpEntity<>(body,headers);
        //发送post请求
        ResponseEntity<String> response=restTemplate.postForEntity(webhookurl,req,String.class);
        logger.info("飞书返回的结果是"+response.getStatusCode());
        //把结果返回给ai,判断状态码是否在200-299
        return response.getStatusCode().is2xxSuccessful()?"已经发送到飞书群"+content:"飞书发送失败"+response.getStatusCode();
    }
}
