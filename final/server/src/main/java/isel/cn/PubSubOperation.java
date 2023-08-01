package isel.cn;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PubSubOperation {

    private static final int DEADLINE = 30;
    private static TopicAdminClient topicAdmin = null;
    private static String PROJECT_ID = null;
    public HashMap<String, Subscriber> subscribers = new HashMap<>();

    public PubSubOperation(String projectId) {
        PROJECT_ID = projectId;
    }

    public final void createSubscription(String topicId, String subsName) {
        try {
            System.out.println("Create subscription");
            TopicName tName = TopicName.ofProjectTopicName(PROJECT_ID, topicId);
            SubscriptionName subscriptionName =
                    SubscriptionName.of(PROJECT_ID, subsName);
            SubscriptionAdminClient subscriptionAdminClient =
                    SubscriptionAdminClient.create();
            PushConfig pConfig = PushConfig.getDefaultInstance();
            subscriptionAdminClient.createSubscription(
                    subscriptionName, tName, pConfig, DEADLINE);
            subscriptionAdminClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final void createSubscriber(String subsName) {
        try {
            System.out.println("create subscriber");
            ProjectSubscriptionName subscriptionName =
                    ProjectSubscriptionName.of(PROJECT_ID, subsName);
            ExecutorProvider executorProvider = InstantiatingExecutorProvider
                    .newBuilder()
                    .setExecutorThreadCount(1)
                    .build();
            Subscriber subscriber =
                    Subscriber.newBuilder(subscriptionName, new LandmarksApp())
                            .setExecutorProvider(executorProvider)
                            .build();
            subscriber.startAsync().awaitRunning();
            subscribers.put(subsName, subscriber);
        } catch (Exception e) {
            throw e;
        }
    }

    public final void publisherMessage(String topicId, String bucketName, String blobName, Identifier identifier) {
        try {
            System.out.println("publisher message");
            TopicName tName = TopicName.ofProjectTopicName(PROJECT_ID, topicId);
            Publisher publisher = Publisher.newBuilder(tName).build();
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .putAttributes("identifier", identifier.getIdentifier())
                    .putAttributes("bucketName", bucketName)
                    .putAttributes("blobName", blobName)
                    .build();
            ApiFuture<String> future = publisher.publish(pubsubMessage);
            String msgID = future.get();
            System.out.println("Message Published with ID=" + msgID);
            publisher.shutdown();
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
