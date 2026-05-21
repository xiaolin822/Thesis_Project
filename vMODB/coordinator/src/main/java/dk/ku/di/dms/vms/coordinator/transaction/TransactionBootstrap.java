package dk.ku.di.dms.vms.coordinator.transaction;

import java.util.*;

/**
 * Responsible for assembling the topology of a transaction crossing virtual microservices
 */
public final class TransactionBootstrap {

    private final List<EventIdentifier> inputEvents; // the input events

    // for fast seek
    private final Map<String, EventIdentifier> inputEventToInternalVMSsMap;

    private final String name; // transaction name

    // to allow the coordinator to efficiently assign the lastTid to each internal node of a transaction
    private final Set<String> internalNodes;

    private final List<String> terminalNodes;

    private final Map<String, String> inputEventToProducerVMSMap;

    private TransactionBootstrap (String name){
        this.name = name;
        this.inputEvents = new ArrayList<>();
        this.internalNodes = new HashSet<>();
        this.terminalNodes = new ArrayList<>();
        this.inputEventToInternalVMSsMap = new HashMap<>();
        this.inputEventToProducerVMSMap = new HashMap<>();
    }

    public static TransactionBootstrap name(String name){
        return new TransactionBootstrap(name);
    }

    public Map<String, String> getInputEventToProducerVMSMap() {
        return inputEventToProducerVMSMap;
    }

    public TransactionBootstrap input(String alias, String vms, String event){
        EventIdentifier id = new EventIdentifier( alias, vms, event );
        this.inputEventToInternalVMSsMap.put( alias, id );
        this.inputEvents.add( id );
        return this;
    }

    public TransactionBootstrap internal(String alias, String vms, String event, String dep){
        EventIdentifier toAdd = new EventIdentifier( alias, vms, event );
        EventIdentifier id = this.inputEventToInternalVMSsMap.get(dep);
        id.addChildren( toAdd );
        this.inputEventToInternalVMSsMap.put( alias, toAdd );
        this.internalNodes.add(vms);
        String producerVMS = id.targetVms;
        this.inputEventToProducerVMSMap.put(event, producerVMS);
        return this;
    }

    /*
    public TransactionBootstrap internal(String alias, String vms, String event, String[] deps){
        if(deps.length == 0) throw new RuntimeException("Cannot have an internal event without a parent event");
        EventIdentifier toAdd = new EventIdentifier( alias, vms, event );
        for(String dep : deps){
            EventIdentifier id = this.inputEventToInternalVMSsMap.get(dep);
            id.addChildren( toAdd );
        }
        this.inputEventToInternalVMSsMap.put( alias, toAdd );
        this.internalNodes.add(vms);
        return this;
    }
    */

    /**
     * Why does terminal not need event input name?
     * Because it can be deducted from the dependence
     * But internal events require the inputs to deduct the precedence set
     */
    public TransactionBootstrap terminal(String alias, String vms, String event, String dep){
        EventIdentifier terminal = new EventIdentifier(alias, vms);
        this.terminalNodes.add(terminal.targetVms);
        EventIdentifier id = this.inputEventToInternalVMSsMap.get(dep);
        // "hack" to allow testing with a single VMS
        if(!vms.contentEquals(id.targetVms)){
            id.addChildren( terminal );
            String producerVMS = id.targetVms;
            this.inputEventToProducerVMSMap.put(event, producerVMS);
        }
        return this;
    }

    // if a terminal has lots of deps????
    public TransactionBootstrap terminal(String alias, String vms, String event, String... deps){
        if(deps == null) throw new RuntimeException("Cannot have a terminal event without a parent event");

        EventIdentifier terminal = new EventIdentifier(alias, vms);
        this.terminalNodes.add(terminal.targetVms);
        for(String dep : deps){
            EventIdentifier id = this.inputEventToInternalVMSsMap.get(dep);
            id.addChildren( terminal );
        }
        return this;
    }

    // finally, build the transaction representation
    public TransactionDAG build(){
        this.inputEvents.sort(Comparator.comparing(o -> o.name));
        return new TransactionDAG(this.name, this.inputEvents, this.internalNodes, this.terminalNodes);
    }

}
