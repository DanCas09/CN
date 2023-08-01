package isel.cn;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class ServerApp extends VisionServiceGrpc.VisionServiceImplBase {
    private static int svcPort = 8000;
    private static StorageOperations soper = null;
    private static PubSubOperation pubsubOper = null;
    private static FirestoreOperation foper = null;
    private static String projID = null;
    private static final String collection = "Landmarks";
    private static final String DEFAULT_BUCKET = "cn-photo-share-eu-madrid";
    private static final String SPLIT_SYMBOL = ";";
    private static final String TOPIC_NAME = "sharefotos";

    public static void main(String[] args) {
        try {
            if (args.length >= 1) {
                svcPort = Integer.parseInt(args[0]);
            }
            io.grpc.Server svc = ServerBuilder.forPort(svcPort).addService(new ServerApp()).build();
            svc.start();
            StorageOptions storageOptions = StorageOptions.getDefaultInstance();
            Storage storage = storageOptions.getService();
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirestoreOptions options = FirestoreOptions
                    .newBuilder().setCredentials(credentials).build();
            Firestore firestore = options.getService();
            projID = storageOptions.getProjectId();
            soper = new StorageOperations(storage);
            if (projID != null) System.out.println("Current Project ID:" + projID);
            else {
                System.out.println("The environment variable GOOGLE_APPLICATION_CREDENTIALS isn't well defined!!");
                System.exit(-1);
            }
            pubsubOper = new PubSubOperation(projID);
            foper = new FirestoreOperation(firestore, collection);
            System.out.println("Server started, listening on " + svcPort);
            while (true) {}
            //svc.shutdown();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public StreamObserver<Blocks> submitImage(StreamObserver<Identifier> responseObserver) {
        return new StreamObserver<Blocks>() {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Blocks image = null;

            @Override
            public void onNext(Blocks blocks) {
                try {
                    image = blocks;
                    outputStream.write(blocks.getBlock().toByteArray());
                } catch (IOException e) {
                    responseObserver.onError(e);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                try {
                    System.out.println(":::Submit Image:::");
                    byte[] fullImage = outputStream.toByteArray();
                    String blobName = image.getImageName() + "_" + UUID.randomUUID();
                    soper.uploadBlobToBucket(DEFAULT_BUCKET, blobName, fullImage);

                    String id = DEFAULT_BUCKET + SPLIT_SYMBOL + blobName;
                    Identifier identifier = Identifier.newBuilder().setIdentifier(id).build();
                    responseObserver.onNext(identifier);

                    pubsubOper.publisherMessage(TOPIC_NAME, DEFAULT_BUCKET, blobName, identifier);

                } catch (Exception e) {
                    responseObserver.onError(e);
                    System.out.println(":::Submit Image Error:::");
                    throw new RuntimeException(e);
                } finally {
                    responseObserver.onCompleted();
                    System.out.println(":::Submit Image Finish:::");
                }
            }
        };
    }

    @Override
    public void getImageInfo(Identifier request, StreamObserver<Results> responseObserver) {
        try {
            System.out.println(":::Image Info:::");

            List<VisionResult> vrs = foper.getImageInfo(request.getIdentifier());

            List<Result> res = new ArrayList<>();

            for (VisionResult result : vrs) {
                if (!result.localizations.isEmpty()) {
                    List<VisionResult.Localization> lst = result.localizations;
                    double x = lst.get(0).x;
                    Localization location = isel.cn.Localization.newBuilder()
                            .setLatitude((float) x)
                            .setLongitude(2)
                            .build();
                    Address address = Address.newBuilder()
                            .setCountry(result.address.country)
                            .setCity(result.address.city)
                            .build();

                    Result responseResult = Result.newBuilder()
                            .setName(result.name)
                            .setCertainty((float) result.confidence)
                            .setLocalization(location)
                            .setAddress(address)
                            .build();

                    res.add(responseResult);
                }
            }
            responseObserver.onNext(Results.newBuilder().addAllResult(res).build());
        } catch (Exception e) {
            System.out.println(":::Image Info Error:::");
            responseObserver.onError(e);
            throw e;
        } finally {
            System.out.println(":::Image Info Finish:::");
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getLocationImage(Identifier request, StreamObserver<Blocks> responseObserver) {
        try {
            System.out.println(":::Location Image:::");
            StorageLocation storageLocation = foper.getLocationRef(request.getIdentifier());
            soper.sendBlobFromBucketToClient(storageLocation.bucketName, storageLocation.blobName, responseObserver);
        } catch (IOException e) {
            System.out.println(":::Location Image Error:::");
            responseObserver.onError(e);
            throw new RuntimeException(e);
        } finally {
            System.out.println(":::Location Image Finish:::");
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getImageNames(SearchRequest request, StreamObserver<ImageNames> responseObserver) {
        try {
            System.out.println(":::Image Names:::");
            List<String> names = foper.getImagesNamesInfo(request.getLocal().toLowerCase(), request.getCertainty());
            ImageNames in = ImageNames.newBuilder().addAllNames(names).build();
            System.out.println(in.getNamesList());
            responseObserver.onNext(in);
        } catch (ExecutionException | InterruptedException e) {
            System.out.println(":::Image Names Error:::");
            responseObserver.onError(e);
            throw new RuntimeException(e);
        } finally {
            System.out.println(":::Image Names Finish:::");
            responseObserver.onCompleted();
        }

    }
}
