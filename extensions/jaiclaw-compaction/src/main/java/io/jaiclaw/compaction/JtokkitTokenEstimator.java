package io.jaiclaw.compaction;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * BPE-based token estimator using JTokkit's cl100k_base encoding.
 * Provides accurate token counts instead of the character-based heuristic.
 */
public class JtokkitTokenEstimator extends TokenEstimator {

    private final Encoding encoding;

    public JtokkitTokenEstimator() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }
}
