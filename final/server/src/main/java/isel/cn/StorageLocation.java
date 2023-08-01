package isel.cn;

public class StorageLocation {
    public String bucketName;
    public String blobName;
    public StorageLocation(String bucketName, String blobName) {
        this.bucketName = bucketName;
        this.blobName = blobName;
    }
}
