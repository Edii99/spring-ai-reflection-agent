package com.example.demo.web;

import com.example.demo.ai.reflection.ReflectionAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/ai/tweets")
public class ReflectionController {

    private final ReflectionAgentService reflectionService;

    public ReflectionController(ReflectionAgentService reflectionService) {
        this.reflectionService = reflectionService;
    }

    @GetMapping("/generate")
    public String generateTweet(@RequestParam String topic) {
        return reflectionService.generateViralTweet(topic);
    }
}
