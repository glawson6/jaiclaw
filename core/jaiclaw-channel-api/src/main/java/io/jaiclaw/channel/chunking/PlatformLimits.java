package io.jaiclaw.channel.chunking;

/**
 * Character limits for outbound messages on each messaging platform.
 * Used by {@link MessageChunker} to split long responses before delivery.
 */
public record PlatformLimits(int maxTextLength) {

    public static final PlatformLimits DEFAULT  = new PlatformLimits(4096);
    public static final PlatformLimits TELEGRAM = new PlatformLimits(4096);
    public static final PlatformLimits SLACK    = new PlatformLimits(40000);
    public static final PlatformLimits DISCORD  = new PlatformLimits(2000);
    public static final PlatformLimits WHATSAPP = new PlatformLimits(65536);
    public static final PlatformLimits SMS      = new PlatformLimits(160);
    public static final PlatformLimits TEAMS    = new PlatformLimits(28000);
    public static final PlatformLimits EMAIL    = new PlatformLimits(Integer.MAX_VALUE);
    public static final PlatformLimits SIGNAL      = new PlatformLimits(65536);
    public static final PlatformLimits LINE        = new PlatformLimits(5000);
    public static final PlatformLimits MATRIX      = new PlatformLimits(65536);
    public static final PlatformLimits GOOGLE_CHAT = new PlatformLimits(4096);
}
