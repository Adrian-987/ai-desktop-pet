package com.example.demo1.controller;

import com.example.demo1.service.ChatService;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:5173}")
public class ChatController {

    Logger logger =  LoggerFactory.getLogger(ChatService.class);

    private final ChatService chatService;

    public ChatController(ChatService  chatService){
        this.chatService=chatService;
    }

    @PostMapping(value="/api/text-chat",produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<String> textChat(@RequestBody  Map<String, String>body){


        return chatService.chatStream(body.get("text"));
    }

}
