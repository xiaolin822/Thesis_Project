package dk.ku.di.dms.shim.model;

public record EventID(String vmsId, long tid) {
    @Override
    public String toString() {
        return vmsId + ":" + tid;
    }

    public static EventID fromString(String raw) {
        String[] parts = raw.split(":");
        return new EventID(parts[0], Long.parseLong(parts[1]));
    }
}