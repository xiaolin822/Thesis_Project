package dk.ku.di.dms.vms.emitter.engine;

import dk.ku.di.dms.vms.emitter.model.EventID;
import dk.ku.di.dms.vms.emitter.model.RawLogRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CausalTag {
    private final LastModifiedTable M = new LastModifiedTable();
    private final AncestorTable A = new AncestorTable();
    private List<String> parseSet(String rawSet) {
        if (rawSet == null || rawSet.length() <= 2) {
            return Collections.emptyList();
        }
        String content = rawSet.substring(1, rawSet.length() - 1);
        if (content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] items = content.split(",");
        List<String> result = new ArrayList<>();
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public Set<EventID> computeCausalTags(RawLogRecord record, Map<String, String> topologyMap) {
        EventID currentEventId = new EventID(record.vmsId(), record.tid());
        Set<EventID> pPhys = new HashSet<>();

        // 1. Workflow Dependency (P_wf)
        String parentVms = topologyMap.get(record.inputQueue());
        if (parentVms != null) {
            EventID parentEventId = new EventID(parentVms, record.tid());
            pPhys.add(parentEventId);
        }

        // 2. Data Dependency (P_data)
        List<String> readObjectIds = parseSet(record.readSetRaw());
        List<String> writeObjectIds = parseSet(record.writeSetRaw());
        Set<String> operationObjectIds = new HashSet<>(readObjectIds);
        operationObjectIds.addAll(writeObjectIds);

        for (String objId : operationObjectIds) {
            EventID lastWriter = M.get(objId);
            if (lastWriter != null) {
                pPhys.add(lastWriter);
            }
        }

        // 3. Logical Dependency Aggregation (Transitive Closure)
        // S_logical = ∪ A[p] for all p in pPhys
        Set<EventID> sLogical = new HashSet<>();
        for (EventID p : pPhys) {
            sLogical.addAll(A.get(p));
        }

        // Global State Update
        if (record.emit()) {
            A.put(currentEventId, Set.of(currentEventId));

        } else {
            A.put(currentEventId, sLogical);
        }

        for (String objId : writeObjectIds) {
            M.update(objId, currentEventId); // M(o) ← ID(n)
        }
        return sLogical;
    }
}