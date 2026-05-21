package dk.ku.di.dms.vms.emitter.adapter;


import dk.ku.di.dms.vms.emitter.model.RawLogRecord;

public class VmsDecoder {
    public static RawLogRecord decode(String line) {
        try {
            // BATCH | VMS_ID | TID | MSG_TYPE | EMIT | Inputevent | TOPIC | RS | WS | PAYLOAD
            String[] parts = line.split("\\|", 10);
            if (parts.length < 10) return null;

            return new RawLogRecord(
                    Long.parseLong(parts[0]),     // batchId
                    parts[1],                    // vmsId
                    Long.parseLong(parts[2]),     // tid
                    parts[3],                    // msgType (LOG/COMMIT)
                    Boolean.parseBoolean(parts[4]), // emit
                    parts[5],                    // inputQueue
                    parts[6],                    // topic
                    parts[7],                    // readSet
                    parts[8],                    // writeSet
                    parts[9]                     // payload
            );
        } catch (Exception e) {
            return null;
        }
    }
}