package io.jaiclaw.perplexity.model;

public record Usage(int promptTokens, int completionTokens, int totalTokens) {}
