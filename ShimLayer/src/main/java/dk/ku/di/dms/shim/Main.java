package dk.ku.di.dms.shim;

import dk.ku.di.dms.shim.config.RouteTable;
import dk.ku.di.dms.shim.consumer.KafkaSubscriber;
import dk.ku.di.dms.shim.core.DependencyChecker;
import dk.ku.di.dms.shim.dispatcher.DaprHttpClient;
import dk.ku.di.dms.shim.model.CausalEvent;
import dk.ku.di.dms.shim.model.EventID;
import dk.ku.di.dms.shim.storage.DeliveredSet;
import dk.ku.di.dms.shim.storage.ProcessingSet;
import dk.ku.di.dms.shim.storage.WaitingMap;

import java.util.List;
import java.util.Set;

public class Main {
    private static final DeliveredSet DELIVERED_SET = new DeliveredSet();
    private static final WaitingMap WAITING_MAP = new WaitingMap();
    private static final ProcessingSet PROCESSING_SET = new ProcessingSet();
    private static final DependencyChecker CHECKER = new DependencyChecker(DELIVERED_SET);
    private static final RouteTable ROUTE_TABLE = new RouteTable();
    private static final DaprHttpClient DAPR_CLIENT = new DaprHttpClient();

    public static void main(String[] args) throws Exception {
        ROUTE_TABLE.loadFromYaml("../MarketplaceOnDapr/dapr.yaml");


        KafkaSubscriber subscriber = new KafkaSubscriber("kafka:9092");
        List<String> topics = ROUTE_TABLE.getDiscoveredTopics();
        if (topics.isEmpty()) {
            System.err.println("[Warning] No Topic！Please make sure you have /dapr/subscribe api");
        } else {
            System.out.println("[Success] subscribe these topics " + topics);
        }

        subscriber.startListening(topics, event -> {
            System.out.println("[Receive msg] ID: " + event.id() + " dependency: " + event.after());
            processEvent(event);
        });
    }

    private static void processEvent(CausalEvent e) {
        if (DELIVERED_SET.isDelivered(e.id())) return;

        Set<EventID> missing = CHECKER.getMissingDependencies(e);

        if (missing.isEmpty()) {
            if (PROCESSING_SET.startProcessing(e.id())) {
                dispatchToDapr(e);
            }
        } else {
            for (EventID depId : missing) {
                WAITING_MAP.put(depId, e);
            }
        }
    }

    private static void dispatchToDapr(CausalEvent e) {
        String url = ROUTE_TABLE.getDaprInvocationUrl(e.topic());
        System.out.println("[DISPATCH] send to Dapr: " + url + " | ID: " + e.id());
        DAPR_CLIENT.post(url, e, Main::onBusinessAck);
    }

    private static void onBusinessAck(EventID id) {
        DELIVERED_SET.add(id);
        PROCESSING_SET.finish(id);

        System.out.println("[ACK] complete: " + id);

        Set<CausalEvent> followers = WAITING_MAP.removeAndGet(id);
        if (followers != null) {
            for (CausalEvent f : followers) {
                System.out.println("[WAKE] : " + f.id());
                processEvent(f);
            }
        }
    }
}