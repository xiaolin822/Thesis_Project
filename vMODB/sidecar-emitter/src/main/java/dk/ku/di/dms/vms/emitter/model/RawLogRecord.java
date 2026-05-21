package dk.ku.di.dms.vms.emitter.model;

public record RawLogRecord(
        long batchId,
        String vmsId,
        long tid,
        String msgType, // LOG or COMMIT
        boolean emit,
        String inputQueue,
        String topic,
        String readSetRaw,
        String writeSetRaw,
        String payload
) {}