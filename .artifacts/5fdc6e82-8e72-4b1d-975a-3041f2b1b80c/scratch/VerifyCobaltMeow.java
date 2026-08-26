import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyCobaltMeow {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            System.out.println("Testing cobalt.meowing.de...");
            URL url = new URL("https://cobalt.meowing.de/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);

            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String json = in.readLine();
                in.close();
                System.out.println("✅ SUCCESS: " + json);
                return;
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        System.out.println("❌ FAILED.");
    }
}
