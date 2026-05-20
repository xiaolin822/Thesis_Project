package dk.ku.di.dms.shim.core;

import dk.ku.di.dms.shim.model.CausalEvent;
import dk.ku.di.dms.shim.model.EventID;
import dk.ku.di.dms.shim.storage.DeliveredSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyChecker {
    private final DeliveredSet deliveredSet;

    public DependencyChecker(DeliveredSet deliveredSet) {
        this.deliveredSet = deliveredSet;
    }


    public Set<EventID> getMissingDependencies(CausalEvent event) {
        return event.after().stream()
                .filter(depId -> !deliveredSet.isDelivered(depId))
                .collect(Collectors.toSet());
    }
}