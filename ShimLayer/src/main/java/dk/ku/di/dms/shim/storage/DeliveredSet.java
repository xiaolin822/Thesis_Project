package dk.ku.di.dms.shim.storage;

import dk.ku.di.dms.shim.model.EventID;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeliveredSet {
    private final Set<EventID> deliveredIds = ConcurrentHashMap.newKeySet();

    public void add(EventID id) { deliveredIds.add(id); }
    public boolean isDelivered(EventID id) { return deliveredIds.contains(id); }
}