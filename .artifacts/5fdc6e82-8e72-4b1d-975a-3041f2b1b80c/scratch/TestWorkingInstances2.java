import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestWorkingInstances2 {
    public static void main(String[] args) {
        String[] instances = {
            "https://piped.video",
            "https://piped.privacydev.net",
            "https://vid.puffyan.us",
            "https://inv.tux.pizza",
            "https://invidious.jing.rocks"
        };
        for (String inst : instances) {
            try {
                String target = inst + "/api/v1/videos/dQw4w9WgXcQ";
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    if (!json.contains("shutdown") && json.length() > 100) {
                        System.out.println("WORKING INSTANCE FOUND: " + inst);
                        System.out.println("Sample: " + json.substring(0, Math.min(json.length(), 300)));
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }
    }
}
