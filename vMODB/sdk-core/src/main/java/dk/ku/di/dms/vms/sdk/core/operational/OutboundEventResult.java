package dk.ku.di.dms.vms.sdk.core.operational;

import dk.ku.di.dms.vms.sdk.core.scheduler.IVmsTransactionResult;

import java.util.Set;

/**
 * Just a placeholder.
 * The object needs to be converted before being sent
 */
public final class OutboundEventResult implements IVmsTransactionResult {

    private final long tid;
    private final long batch;
    private final String outputQueue;
    private final Object output;
    private final Set<String> readSet;
    private final Set<String> writeSet;
    private final String inputQueue;
    private final VmsTransactionSignature signature;

    public OutboundEventResult(long tid, long batch, String outputQueue, Object output, Set<String> readSet,
                               Set<String> writeSet,String inputQueue, VmsTransactionSignature signature) {
        this.tid = tid;
        this.batch = batch;
        this.outputQueue = outputQueue;
        this.output = output;
        this.readSet = readSet;
        this.writeSet = writeSet;
        this.inputQueue = inputQueue;
        this.signature = signature;
    }

    @Override
    public long tid() {
        return this.tid;
    }

    @Override
    public OutboundEventResult getOutboundEventResult() {
        return this;
    }

    public String outputQueue() {
        return this.outputQueue;
    }

    public long batch() {
        return this.batch;
    }

    public Object output() {
        return this.output;
    }

    public Set<String> readSet() {
        return this.readSet;
    }

    public Set<String> writeSet() {
        return this.writeSet;
    }

    public String inputQueue() {
        return this.inputQueue;
    }

    public VmsTransactionSignature signature() {
        return this.signature;
    }
}