package io.jaiclaw.perplexity.model;

public record Choice(int index, String finishReason, Message message) {}
