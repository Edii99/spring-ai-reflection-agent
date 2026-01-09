package com.example.demo.ai.reflection;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReflectionAgentService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ReflectionAgentService.class);

    // Node Constants
    public static final String GENERATE_NODE = "generate";
    public static final String REFLECT_NODE = "reflect";
    public static final String END = "end";

    private final ChatClient chatClient;
    
    @Value("classpath:/prompts/generation-writer.st")
    private Resource writerSystemPrompt;
    
    @Value("classpath:/prompts/reflection-critic.st")
    private Resource criticSystemPrompt;

    public ReflectionAgentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateViralTweet(String topic) {
        // Use ChatMemory to manage conversation history automatically for this request.
        var memory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
        
        // 1. Initial Call -> Trigger Generation Node
        logger.info("Starting Node: {}", GENERATE_NODE);
        String currentTweet = generationNode("Escribe un tweet sobre: " + topic, memoryAdvisor);
        
        // Loop (Graph Execution)
        int maxIterations = 3;
        for (int i = 0; i < maxIterations; i++) {
            logger.info("Iteration {}: {}", i + 1, currentTweet);

            // 2. Reflection Node
            logger.info("Entering Node: {}", REFLECT_NODE);
            TweetCritique critique = reflectionNode(currentTweet);
            logger.info("Critique: {}", critique.critique());

            if (critique.status() == TweetCritique.Status.PERFECT) {
                logger.info("End Condition Met: {}", END);
                return currentTweet;
            }

            // 3. Generation Node (Regenerate based on critique)
            String critiqueInput = formatCritiqueInstruction(critique);
            
            logger.info("Entering Node: {}", GENERATE_NODE);
            currentTweet = generationNode(critiqueInput, memoryAdvisor);
        }

        return currentTweet;
    }

    /**
     * Generation Node
     * Corresponds to 'generation_chain' in the requirement.
     * Uses MessageChatMemoryAdvisor to maintain state (history).
     */
    private String generationNode(String userInput, MessageChatMemoryAdvisor memoryAdvisor) {
        return chatClient.prompt()
                .advisors(memoryAdvisor)
                .system(loadResource(writerSystemPrompt))
                .user(userInput)
                .call()
                .content();
    }

    /**
     * Reflection Node
     * Corresponds to 'reflect_chain'.
     */
    private TweetCritique reflectionNode(String tweet) {
        return chatClient.prompt()
                .user(u -> u.text("Aquí está el tweet para criticar: {tweet}").param("tweet", tweet))
                .system(loadResource(criticSystemPrompt))
                .call()
                .entity(TweetCritique.class);
    }
    
    private String formatCritiqueInstruction(TweetCritique critique) {
        return String.format("""
                Crítica: %s
                Recomendaciones: %s
                Score Viral: %d/10
                Por favor mejora el tweet basándote en esto.
                """, critique.critique(), 
                   critique.recommendations() != null ? String.join(", ", critique.recommendations()) : "None", 
                   critique.viralScore());
    }
    
    private String loadResource(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load prompt resource", e);
        }
    }
}
