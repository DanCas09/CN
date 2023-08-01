package isel.cn;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Random;

import java.io.*;
import java.util.List;
import java.util.Objects;

public class Client {
    static String svcIP = "localhost";
    static int svcPort = 8000;
    private static ManagedChannel channel;
    private static VisionServiceGrpc.VisionServiceStub noBlockStub;


    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                svcIP = args[0];
                svcPort = Integer.parseInt(args[1]);
            }

            String ip = connect();
            while(ip == null) {
                Thread.sleep(1500);
                System.out.println("Lost connection");
                System.out.println("Retry connection");
                ip = connect();
            }

            svcIP = ip;
            channel = ManagedChannelBuilder.forAddress(svcIP, svcPort)
                    .usePlaintext()
                    .build();

            noBlockStub = VisionServiceGrpc.newStub(channel);
            System.out.println("Connected " + svcIP + ":" + svcPort);
            showCommandMenu();
            menu();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static String connect() {
        List<String> lookups = new LookupAccess().getInstanceIp("europe-west1-b");
        int size = lookups.size();
        if (size == 0) {
            return null;
        } else {
            int randomIdx = new Random().nextInt(size);
            return lookups.get(randomIdx);
        }
    }


    public static void showCommandMenu() {
        System.out.println(" Menu:\n "+ "1 -> submit image\n"+
                "2 -> get info\n"+
                "3 -> get location\n"+
                "4 -> get imageNames\n"+
                "exit -> exit\n"+
                "l -> menu\n"+
                "\n");
    }

    public static void menu() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String n = br.readLine();
        while (!Objects.equals(n, "exit")) {
            switch (n) {
                case "1" : submitImage(noBlockStub);
                    break;
                case "2" : getInfo(noBlockStub);
                    break;
                case "3" : getLocations(noBlockStub);
                    break;
                case "4" : getImageNames(noBlockStub);
                    break;
                case "l" : showCommandMenu();
                    break;
                default : menu();
            }
            n = br.readLine();
        }
    }

    private static void getImageNames(VisionServiceGrpc.VisionServiceStub noBlockStub) {
        try {
            // location -> city name
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("The city: ");
            String location = br.readLine();
            System.out.println("Certainty level: (< 1.0)");
            float confidence = Float.parseFloat(br.readLine());

            CustomStreamObserver<ImageNames> imageNamesStreamObserver = new CustomStreamObserver<>();
            noBlockStub.getImageNames(SearchRequest.newBuilder()
                    .setLocal(location)
                    .setCertainty(confidence)
                    .build(), imageNamesStreamObserver);

            System.out.println("I am waiting for results");
            while (!imageNamesStreamObserver.isCompleted) {
                Thread.sleep(1000);
            }
            System.out.println("Done!");
            System.out.println("Image Names:");
            System.out.println(imageNamesStreamObserver.resImageNames);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void submitImage(VisionServiceGrpc.VisionServiceStub noBlockStub) {
        try {
            System.out.println("Submit image");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("The image path");
            String imagePath = br.readLine();
            String[] split = imagePath.split("/");
            String name = split[split.length - 1];
            CustomStreamObserver<Identifier> idObserver = new CustomStreamObserver<>();
            StreamObserver<Blocks> streamObserver = noBlockStub.submitImage(idObserver);
            try (FileInputStream reader = new FileInputStream(imagePath)) {
                byte[] bytes = new byte[1024];
                while (reader.read(bytes) > 0) {
                    var image = Blocks.newBuilder();
                    image.setImageName(name)
                            .setBlock(ByteString.copyFrom(bytes))
                            .build();
                    streamObserver.onNext(image.build());
                }
                streamObserver.onCompleted();

                System.out.println("I am waiting for identifier");
                while (!idObserver.isCompleted) {
                    Thread.sleep(1000);
                }

                System.out.println("Done!");
                System.out.println("Image Identifier:");
                System.out.println(idObserver.result.getIdentifier());

            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getInfo(VisionServiceGrpc.VisionServiceStub noBlockStub) {
        try {
            System.out.println("Get image info");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("The identifier");
            String identifier = br.readLine();

            CustomStreamObserver<Results> resultsObserver = new CustomStreamObserver<>();
            noBlockStub.getImageInfo(Identifier.newBuilder().setIdentifier(identifier).build(), resultsObserver);

            System.out.println("I am waiting for results");
            while (!resultsObserver.isCompleted) {
                Thread.sleep(1000);
            }
            System.out.println("Done!");
            System.out.println("Image Info:");
            System.out.println(resultsObserver.result.toString());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getLocations(VisionServiceGrpc.VisionServiceStub noBlockStub) {
        try {
            System.out.println("Get map");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("The identifier");
            String identifier = br.readLine();
            CustomStreamObserver<Blocks> blocksObserver = new CustomStreamObserver<>();
            noBlockStub.getLocationImage(Identifier.newBuilder().setIdentifier(identifier).build(), blocksObserver);

            System.out.println("I am waiting for Image");
            while (!blocksObserver.isCompleted) {
                Thread.sleep(1000);
            }
            System.out.println("Done!");
            System.out.println("Image name:");
            System.out.println(blocksObserver.name);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}