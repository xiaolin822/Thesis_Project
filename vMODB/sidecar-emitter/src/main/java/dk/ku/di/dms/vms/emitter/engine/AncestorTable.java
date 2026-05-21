package dk.ku.di.dms.vms.emitter.engine;

import dk.ku.di.dms.vms.emitter.model.EventID;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AncestorTable {
    private final Map<EventID, Set<EventID>> table = new ConcurrentHashMap<>();

    public void put(EventID id, Set<EventID> ancestors) {
        table.put(id, ancestors);
    }

    public Set<EventID> get(EventID id) {
        return table.getOrDefault(id, Collections.emptySet());
    }
}