import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindInvidious {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] instances = {
            "https://invidious.privacyredirect.com",
            "https://invidious.projectsegfau.lt",
            "https://vid.puffyan.us",
            "https://inv.nadeko.net",
            "https://invidious.io.lol"
        };
        for (String instance : instances) {
            try {
                System.out.println("Testing: " + instance);
                URL url = new URL(instance + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    System.out.println("✅ WORKING INSTANCE: " + instance);
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line = in.readLine();
                    if (line != null && line.contains("adaptiveFormats")) {
                        System.out.println("✅ DATA VERIFIED!");
                        return;
                    }
                    in.close();
                }
            } catch (Exception e) { System.out.println("Failed: " + instance); }
        }
    }
}
