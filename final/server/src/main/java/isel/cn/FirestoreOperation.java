package isel.cn;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FirestoreOperation {

    private Firestore firestore = null;
    private String collection = null;


    public FirestoreOperation(Firestore firestore, String collection) {
        this.firestore = firestore;
        this.collection = collection;
    }

    public void createDocument(VisionResult vrs) {
        try {
            System.out.println("Create document");
            CollectionReference colRef = firestore.collection(collection);
            DocumentReference docRef = colRef.document();
            ApiFuture<WriteResult> result = docRef.set(vrs);
            System.out.println("Update time:" + result.get().getUpdateTime());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private List<DocumentSnapshot> getDocById(String id) {
        try {
            CollectionReference colRef = firestore.collection(collection);
            Query query = colRef.whereEqualTo("ID", id);
            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            List<QueryDocumentSnapshot> docs = querySnapshot.get().getDocuments();
            return new ArrayList<>(docs);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public StorageLocation getLocationRef(String identifier) {
        try {
            System.out.println("Get map image ref");
            List<DocumentSnapshot> documentSnapshots = getDocById(identifier);
            if (documentSnapshots.isEmpty()) {
                return null;
            } else {
                HashMap<String, String> staticMapRef = (HashMap<String, String>) documentSnapshots.get(0).get("staticMapRef");
                return new StorageLocation(staticMapRef.get("bucketName"), staticMapRef.get("blobName"));
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public List<VisionResult> getImageInfo(String identifier) {
        try {
            System.out.println("Get image info");
            ArrayList<VisionResult> visionResults = new ArrayList<VisionResult>();
            List<DocumentSnapshot> documentSnapshots = getDocById(identifier);
            if (documentSnapshots.isEmpty()) {
                return null;
            } else {
                for (DocumentSnapshot doc : documentSnapshots) {
                    //TODO -> converter de forma mais eficaz os docs em objects VisionResult
                    List<VisionResult.Localization> localizations = new ArrayList<>();
                    List<HashMap<String, Double>> values = (List<HashMap<String, Double>>) doc.get("localizations");
                    assert values != null;
                    for (HashMap<String, Double> coords : values) {
                        localizations.add(new VisionResult.Localization(coords.get("x"), coords.get("y")));
                    }
                    StorageLocation storageLocation = new StorageLocation((String) doc.get("staticMapRef.bucketName"), (String) doc.get("staticMapRef.blobName"));
                    String id = (String) doc.get("ID");
                    String name = (String) doc.get("name");
                    double confidence = doc.get("confidence", double.class);

                    HashMap<String, String> address = (HashMap<String, String>) doc.get("address");

                    visionResults.add((new VisionResult(id, name, confidence, localizations, storageLocation, new VisionResult.Address(address.get("country"), address.get("city")) )));
                }
                return visionResults;
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public List<String> getImagesNamesInfo(String city, float confidence) throws ExecutionException, InterruptedException {
        FieldPath fCountry = FieldPath.of("address", "city");
        Query query = firestore.collection(collection)
                .whereEqualTo(fCountry, city)
                .whereGreaterThan("confidence", confidence);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<String> resNames = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.get().getDocuments()) {
            resNames.add(doc.get("name", String.class));
        }
        return resNames;
    }

}
