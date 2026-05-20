package dk.ku.di.dms.shim.model;

import java.util.Set;

public record CausalEvent(
        EventID id,
        Set<EventID> after,
        String topic,
        String payload
) {}