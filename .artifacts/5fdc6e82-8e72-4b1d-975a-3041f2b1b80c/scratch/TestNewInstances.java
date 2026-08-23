import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNewInstances {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] instances = {
            "https://pipedapi.kavin.rocks",
            "https://piped.video",
            "https://inv.nadeko.net",
            "https://invidious.io.lol",
            "https://yt.artemislabs.eu"
        };

        for (String instance : instances) {
            try {
                String target = instance + "/api/v1/videos/" + videoId;
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                System.out.println("Response Code: " + code);
                if (code >= 200 && code < 300) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    System.out.println("Response length: " + json.length());
                    if (json.contains("audioStreams") || json.contains("adaptiveFormats") || json.contains("url") || json.length() > 100) {
                        System.out.println("SUCCESS! Valid content received from " + instance);
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error with " + instance + ": " + e.getMessage());
            }
        }
        System.out.println("ALL INSTANCES FAILED.");
    }
}
