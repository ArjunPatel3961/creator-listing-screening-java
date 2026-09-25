package dev.infrai.creator;

import java.util.List;

public record PublicationDecision(State state, List<String> reasons) {
    public enum State { READY_FOR_SUBSCRIBERS, QUARANTINED }

    public static PublicationDecision from(boolean flagged, List<String> categories) {
        return flagged
                ? new PublicationDecision(State.QUARANTINED, List.copyOf(categories))
                : new PublicationDecision(State.READY_FOR_SUBSCRIBERS, List.of());
    }
}
