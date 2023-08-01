package isel.cn;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.ListInstancesRequest;
import com.google.cloud.firestore.*;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class Lookup implements HttpFunction {
    private static final String PROJECT_ID = "cn2223-t1-g07";
    private static final Logger logger = Logger.getLogger(Lookup.class.getName());
    private static final String ZONE = "europe-west1-b";
    private static final String INSTANCE_GROUP = "instance-group-monument-finder";

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        BufferedWriter writer = response.getWriter();
        String zone = request.getFirstQueryParameter("zone").orElse(ZONE);
        String instanceGroup = request.getFirstQueryParameter("instance-group").orElse(INSTANCE_GROUP);
        logger.info("Zone: " + zone);
        logger.info("InstanceGroup: " + instanceGroup);
        try (InstancesClient client = InstancesClient.create()) {
            for (Instance instance : client.list(PROJECT_ID, zone).iterateAll()) {
                logger.info("instance: " + instance.getName());
                if (instance.getStatus().compareTo("RUNNING") == 0 && instance.getName().contains(instanceGroup)) {
                    logger.info("Response instance: " + instance.getName());
                    String ip = instance.getNetworkInterfaces(0).getAccessConfigs(0).getNatIP();
                    writer.write(ip+";");
                }
            }
        }
    }

}