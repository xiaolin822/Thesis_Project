package dk.ku.di.dms.vms.emitter.model;

public record EventID(String vmsId, long tid) {
    @Override
    public String toString() {
        return vmsId + ":" + tid;
    }
}