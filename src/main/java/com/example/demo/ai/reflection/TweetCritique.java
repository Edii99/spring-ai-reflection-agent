package com.example.demo.ai.reflection;

import java.util.List;

public record TweetCritique(
    String critique,
    List<String> recommendations,
    int viralScore,
    Status status
) {
    public enum Status {
        IMPROVE,
        PERFECT
    }
}
