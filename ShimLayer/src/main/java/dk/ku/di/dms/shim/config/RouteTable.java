package dk.ku.di.dms.shim.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class RouteTable {
    private final Map<String, Integer> daprPorts = new HashMap<>(); // appId -> 350x
    private final Map<String, String> topicToAppId = new HashMap<>(); // Topic -> appId
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void loadFromYaml(String path) throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode root = yamlMapper.readTree(new File(path));
        JsonNode apps = root.get("apps");

        if (apps != null && apps.isArray()) {
            for (JsonNode app : apps) {
                String appId = app.get("appID").asText();
                int daprPort = app.get("daprHTTPPort").asInt();
                int appPort = app.get("appPort").asInt(); // C# port

                daprPorts.put(appId, daprPort);

                discoverTopics(appId, appPort);
            }
        }
    }

    private void discoverTopics(String appId, int appPort) {
        try {
            // Dapr pub/sub
            String url = "http://seller-ms:5006/dapr/subscribe";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                //[{"pubsubname": "pubsub", "topic": "InvoiceIssued", "route": "..."}]
                JsonNode subscriptions = jsonMapper.readTree(response.body());
                for (JsonNode sub : subscriptions) {
                    String topic = sub.get("topic").asText();
                    topicToAppId.put(topic, appId);
                    System.out.println("[RouteTable] find: Topic[" + topic + "] -> App[" + appId + "]");
                }
            }
        } catch (Exception e) {
            System.err.println("[RouteTable] Can't " + appId + " find Topic (maybe service is not running)");
        }
    }

    public String getDaprInvocationUrl(String topic) {
        String appId = topicToAppId.get(topic);
        if (appId == null) return null;
        return String.format("http://seller-dapr:3506/v1.0/invoke/seller/method/ProcessNewInvoice",
                daprPorts.get(appId), appId, topic);
    }

    public List<String> getDiscoveredTopics() {
        return new ArrayList<>(topicToAppId.keySet());
    }
}