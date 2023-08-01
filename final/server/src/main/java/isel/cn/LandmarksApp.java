package isel.cn;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vision.v1.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.google.pubsub.v1.*;
import com.google.type.LatLng;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class LandmarksApp implements MessageReceiver {
    private static PubSubOperation pubSubOperation;
    private static StorageOperations storageOperations;
    private static FirestoreOperation firestoreOperation;
    private static String collection = "Landmarks";
    private static String PROJECT_ID;
    private static final String TOPIC_NAME = "sharefotos";
    private static final String SUBSCRIPTION = "workers";
    final static int ZOOM = 15;
    final static String SIZE = "600x300";
    private static String API_KEY;

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("API Key missing");
                System.out.println("Usage: java -jar LandmarkDetector.jar <API_KEY>");
                System.exit(1);
            }
            API_KEY = args[0];
            StorageOptions storageOptions = StorageOptions.getDefaultInstance();
            Storage storage = storageOptions.getService();
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirestoreOptions options = FirestoreOptions
                    .newBuilder().setCredentials(credentials).build();
            Firestore firestore = options.getService();
            firestoreOperation = new FirestoreOperation(firestore, collection);
            PROJECT_ID = storageOptions.getProjectId();
            storageOperations = new StorageOperations(storage);
            pubSubOperation = new PubSubOperation(PROJECT_ID);
            if (PROJECT_ID != null) System.out.println("Current Project ID:" + PROJECT_ID);
            else {
                System.out.println("The environment variable GOOGLE_APPLICATION_CREDENTIALS isn't well defined!!");
                System.exit(-1);
            }
            createSubscription();
            while (true) {

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {
        try {
            String data = pubsubMessage.getData().toStringUtf8();
            Map<String, String> attributes = pubsubMessage.getAttributesMap();
            System.out.println("Message (Id:" + pubsubMessage.getMessageId() +
                    " Data:" + data + ")");
            String bucketName = attributes.get("bucketName");
            String blobName = attributes.get("blobName");
            String identifier = attributes.get("identifier");
            ackReplyConsumer.ack();

            List<VisionResult> results = detectLandmarksGcs(identifier, bucketName, blobName, API_KEY);

            assert results != null;
            for (VisionResult result : results) {
                firestoreOperation.createDocument(result);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void createSubscription() {
        try {
            System.out.println(":::Subscribe:::");
            try {
                pubSubOperation.createSubscription(TOPIC_NAME, SUBSCRIPTION);
            } catch (Exception e) {
                if (!Objects.equals(e.getLocalizedMessage().split(":")[1], " ALREADY_EXISTS")) {
                    throw e;
                } else {
                    System.out.println("The subscription is already created");
                }
            }
            pubSubOperation.createSubscriber(SUBSCRIPTION);
        } catch (Exception e) {
            System.out.println(":::Subscribe Error:::");
            throw e;
        } finally {
            System.out.println(":::Subscribe Finish:::");
        }
    }

    public static List<VisionResult> detectLandmarksGcs(String identifier, String bucketName, String blobName, String apiKey) throws IOException {

        String gcsPath = "gs://" + bucketName + "/" + blobName;
        List<AnnotateImageRequest> requests = new ArrayList<>();
        List<VisionResult> results = new ArrayList<>();
        String processedBlobName = null;
        ImageSource imgSource = ImageSource.newBuilder().setGcsImageUri(gcsPath).build();
        Image img = Image.newBuilder().setSource(imgSource).build();

        Feature feat = Feature.newBuilder().setType(Feature.Type.LANDMARK_DETECTION).build();
        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder()
                        .addFeatures(feat)
                        .setImage(img)
                        .build();

        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (responses.isEmpty()) {
                System.out.println("Empty response, no object detected.");
                return null;
            }
            boolean first = true;
            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    System.out.format("Error: %s%n", res.getError().getMessage());
                    return null;
                }
                List<VisionResult.Localization> localizations = new ArrayList<>();

                for (EntityAnnotation annotation : res.getLandmarkAnnotationsList()) {

                    VisionResult.Address address = null;
                    boolean firstCoordinate = true;
                    System.out.println("Landmarks list size: " + res.getLandmarkAnnotationsList().size());

                    List<LocationInfo> annotationLocalizations = annotation.getLocationsList();
                    String description = annotation.getDescription();
                    float score = annotation.getScore();

                    for (LocationInfo locationInfo : annotationLocalizations) {
                        LatLng coordinate = locationInfo.getLatLng();
                        VisionResult.Localization localization = new VisionResult.Localization(coordinate.getLatitude(), coordinate.getLongitude());
                        localizations.add(localization);
                        if (first) {
                            processedBlobName = description.replace(" ", "-") + "_staticMap_" + UUID.randomUUID() + ".png";
                            getStaticMapSaveImage(coordinate, apiKey, bucketName, processedBlobName);
                            first = false;
                        }
                        if (firstCoordinate) {
                            address = getAddressWithCoordinates(localization, apiKey);
                            firstCoordinate = false;
                        }
                    }
                    VisionResult result = new VisionResult(identifier, description, score, localizations, new StorageLocation(bucketName, processedBlobName), address);
                    results.add(result);
                    System.out.format("Landmark: %s(%f)%n %s%n",
                            result.name,
                            result.confidence,
                            result.localizations.toString());
                }
            }
            return results;
        }
    }

    public static VisionResult.Address getAddressWithCoordinates(VisionResult.Localization localization, String apiKey) throws IOException {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=" + localization.x + "," + localization.y + "&key=" + apiKey;
        System.out.println(url);
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();

        String locality = null;
        String country = null;

        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null)
                response.append(inputLine);
            in.close();

            // Cria um JsonReader
            JsonReader reader = Json.createReader(new StringReader(response.toString()));

            // Faz o parse da string JSON para um objeto JsonObject
            JsonObject jsonObject = reader.readObject();

            // Fecha o reader
            reader.close();

            // Obtém os objetos do array results
            JsonArray results = jsonObject.getJsonArray("results");

            if (results.size() > 0) {
                JsonObject firstResult = results.getJsonObject(0);
                JsonArray addressComponents = firstResult.getJsonArray("address_components");

                for (int i = 0; i < addressComponents.size(); i++) {
                    JsonObject component = addressComponents.getJsonObject(i);
                    JsonArray types = component.getJsonArray("types");

                    for (int j = 0; j < types.size(); j++) {
                        String type = types.getString(j);

                        if (type.equals("locality")) {
                            locality = component.getString("long_name");
                        } else if (type.equals("country")) {
                            country = component.getString("long_name");
                        }
                    }
                }

            }
        } else {
            System.out.println("GET request not worked");
        }
        return new VisionResult.Address(country.toLowerCase(), locality.toLowerCase());
    }

    private static void getStaticMapSaveImage(LatLng latLng, String apiKey, String bucketName, String blobName) {
        String mapUrl = "https://maps.googleapis.com/maps/api/staticmap?"
                + "center=" + latLng.getLatitude() + "," + latLng.getLongitude()
                + "&zoom=" + ZOOM
                + "&size=" + SIZE
                + "&key=" + apiKey;
        System.out.println(mapUrl);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            URL url = new URL(mapUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            InputStream in = conn.getInputStream();
            BufferedInputStream bufIn = new BufferedInputStream(in);
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = bufIn.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            bufIn.close();
            in.close();
            byte[] fullImage = outputStream.toByteArray();
            storageOperations.uploadBlobToBucket(bucketName, blobName, fullImage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
