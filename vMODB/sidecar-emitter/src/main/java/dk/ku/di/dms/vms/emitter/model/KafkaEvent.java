package dk.ku.di.dms.vms.emitter.model;

import java.util.Set;


public record KafkaEvent(
        long tid,
        String vmsId,
        String topic,      // externalTopic
        String payload,
        Set<EventID> causalTags,
        long batchId
) {}