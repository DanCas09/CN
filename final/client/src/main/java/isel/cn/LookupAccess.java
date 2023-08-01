package isel.cn;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LookupAccess {
    String instanceGroup = "instance-group-monument-finder";
    public List<String> getInstanceIp(String zone) {
        try {
            String cfURL = "https://europe-west1-cn2223-t1-g07.cloudfunctions.net/cn-http-function-lookup?" + "instance-group=" + instanceGroup + "&&" + "zone=" + zone;
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfURL))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String result = response.body();
                if (result.isEmpty()) {
                    return new ArrayList<>();
                } else {
                    return List.of(result.split(";"));
                }
            } else {
                System.out.println("error with: " + response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
