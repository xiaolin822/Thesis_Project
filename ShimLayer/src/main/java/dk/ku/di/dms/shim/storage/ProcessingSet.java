package dk.ku.di.dms.shim.storage;

import dk.ku.di.dms.shim.model.EventID;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessingSet {
    // 记录已经发往 Dapr 但还没收到 ACK 的消息，防止重复推送
    private final Set<EventID> processing = ConcurrentHashMap.newKeySet();

    public boolean startProcessing(EventID id) { return processing.add(id); }
    public void finish(EventID id) { processing.remove(id); }
}