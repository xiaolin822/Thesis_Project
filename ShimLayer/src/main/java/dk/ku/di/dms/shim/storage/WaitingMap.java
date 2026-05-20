package dk.ku.di.dms.shim.storage;

import dk.ku.di.dms.shim.model.CausalEvent;
import dk.ku.di.dms.shim.model.EventID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class WaitingMap {
    // Key: 被依赖的 ID
    // Value: 正在等待该 ID 的事件集合 (Set 保证唯一性)
    private final Map<EventID, Set<CausalEvent>> waitingRoom = new ConcurrentHashMap<>();

    /**
     * put events into waiting_map
     */
    public void put(EventID dependsOnId, CausalEvent event) {
        // 使用 ConcurrentHashMap.newKeySet() 创建线程安全的 Set
        waitingRoom.computeIfAbsent(dependsOnId, k -> ConcurrentHashMap.newKeySet())
                .add(event);
    }

    /**
     * get and remove all the events that are dependent on this
     */
    public Set<CausalEvent> removeAndGet(EventID dependsOnId) {
        return waitingRoom.remove(dependsOnId);
    }
}