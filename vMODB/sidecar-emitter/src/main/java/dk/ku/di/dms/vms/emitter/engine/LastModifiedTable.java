package dk.ku.di.dms.vms.emitter.engine;

import dk.ku.di.dms.vms.emitter.model.EventID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LastModifiedTable {
    // Key: ObjectId (vms:table:pk), Value: EventID
    private final Map<String, EventID> table = new ConcurrentHashMap<>();

    public void update(String objectId, EventID eventId) {
        table.put(objectId, eventId);
    }

    public EventID get(String objectId) {
        return table.get(objectId);
    }
}