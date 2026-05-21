package dk.ku.di.dms.vms.sdk.embed.handler;

import dk.ku.di.dms.vms.sdk.core.operational.OutboundEventResult;


public record ExternalEventRecord(long batchId, String system, String topic, int count, OutboundEventResult event) {
}