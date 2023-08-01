package isel.cn;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.InstanceGroupManagersClient;
import com.google.cloud.compute.v1.InstanceGroupManagersSettings;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.firestore.*;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;

import java.io.IOException;
import java.util.logging.Logger;

public class Monitor implements BackgroundFunction<PSmessage> {
    private static Firestore db = initFirestore();
    private static final String PROJECT_ID = "cn2223-t1-g07";
    private static final String COLLECTION = "Monitor";
    private static final String DOCUMENT = "State";
    private static final String ZONE = "europe-west1-b";
    private static final String SERVER_INSTANCE_GROUP = "instance-group-monument-finder";
    private static final int MAX_SERVER = 3;
    private static final int MIN_SERVER = 1;
    private static final int MAX_LANDMARKS = 2;
    private static final int MIN_LANDMARKS = 0;

    private static Firestore initFirestore() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirestoreOptions options = FirestoreOptions.newBuilder().setCredentials(credentials).build();
            Firestore db = options.getService();
            return db;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void accept(PSmessage pSmessage, Context context) throws Exception {
        String zone = pSmessage.attributes.getOrDefault("zone", ZONE);
        String instanceGroup = pSmessage.attributes.getOrDefault("instance-group", SERVER_INSTANCE_GROUP);
        String size = pSmessage.attributes.getOrDefault("size", String.valueOf(0));
        int newSize = Integer.parseInt(size);
        if (instanceGroup.contains(SERVER_INSTANCE_GROUP)) {
            if (newSize > MAX_SERVER) {
                newSize = MAX_SERVER;
            }
            if (newSize < MIN_SERVER) {
                newSize = MIN_SERVER;
            }
        } else {
            if (newSize > MAX_LANDMARKS) {
                newSize = MAX_LANDMARKS;
            }
            if (newSize < MIN_LANDMARKS) {
                newSize = MIN_LANDMARKS;
            }
        }
        CollectionReference colRef = db.collection(COLLECTION);
        DocumentReference docRef = colRef.document(DOCUMENT);
        if (instanceGroup.contains(SERVER_INSTANCE_GROUP)) {
            docRef.update("server", newSize);
        } else {
            docRef.update("landmarks", newSize);
        }

        InstanceGroupManagersSettings settings = InstanceGroupManagersSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.getApplicationDefault()))
                .build();
        InstanceGroupManagersClient managersClient = InstanceGroupManagersClient.create(settings);

        OperationFuture<Operation, Operation> result = managersClient.resizeAsync(
                PROJECT_ID,
                zone,
                instanceGroup,
                newSize
        );
        result.get();
    }
}