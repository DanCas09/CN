package isel.cn;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StorageOperations {

    Storage storage = null;

    public StorageOperations(Storage storage) {
        this.storage = storage;
    }

    private static final int BUFFER_SIZE = 1024;

    public final void makeObjectPublic(String bucketName, String objectName) {
        BlobId blobId = BlobId.of(bucketName, objectName);
        storage.createAcl(blobId, Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
        System.out.println(
                "Object " + objectName + " in bucket " + bucketName + " was made publicly readable");
    }

    public void uploadBlobToBucket(String bucketName, String blobName, byte[] image) throws Exception {
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/png").build();
        int size = image.length;
        if (size > 1_000_000) {
            // When content is not available or large (1MB or more) it is recommended
            // to write it in chunks via the blob's channel writer.
            try (WriteChannel writer = storage.writer(blobInfo)) {
                for (int i = 0; i < size; i += BUFFER_SIZE) {
                    int limit = Math.max(i + BUFFER_SIZE, size);
                    writer.write(ByteBuffer.wrap(image, i, limit));
                }
            }
        } else {
            storage.create(blobInfo, image);
        }
        makeObjectPublic(bucketName, blobName);
        System.out.println("Blob " + blobName + " created in bucket " + bucketName);
    }

    public void sendBlobFromBucketToClient(String bucketName, String blobName, StreamObserver<Blocks> responseObserver) throws IOException {

        System.out.println("Send blob");
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            System.out.println("No such Blob exists !");
            return;
        }

        if (blob.getSize() < 1_000_000) {
            // Blob is small read all its content in one request
            byte[] content = blob.getContent();
            responseObserver.onNext(
                    Blocks.newBuilder()
                            .setImageName(blobName)
                            .setBlock(ByteString.copyFrom(content))
                            .build());
        } else {
            // When Blob size is big or unknown use the blob's channel reader.
            try (ReadChannel reader = blob.reader()) {
                ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
                while (reader.read(bytes) > 0) {
                    bytes.flip();
                    Blocks blocks = Blocks.newBuilder()
                                    .setImageName(blobName)
                                    .setBlock(ByteString.copyFrom(bytes.array()))
                                    .build();
                    responseObserver.onNext(blocks);
                    bytes.clear();
                }
            }
        }
        System.out.println("Download Blob: " + blobName);
    }

}

