package isel.cn;

import java.util.List;

public class VisionResult {
    public String ID;
    public String name;
    public double confidence;
    public List<Localization> localizations;
    public StorageLocation staticMapRef;
    public Address address;

    public static class Localization {
        public double x;
        public double y;

        public Localization(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Address {
        public String country;
        public String city;

        public Address(String country, String city) {
            this.country = country;
            this.city = city;
        }
    }

    public VisionResult(String ID, String name, double confidence, List<Localization> localizations, StorageLocation staticMapRef, Address address) {
        this.ID = ID;
        this.name = name;
        this.confidence = confidence;
        this.localizations = localizations;
        this.staticMapRef = staticMapRef;
        this.address = address;
    }
}
