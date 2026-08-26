import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyCobaltV2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing Cobalt v2 for U2...");
            URL url = new URL("https://cobalt.canine.tools/api/json"); // Using canine tools mirror
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String body = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String json = reader.readLine();
                System.out.println("Cobalt SUCCESS!");
                if (json.contains("\"url\":\"")) {
                    System.out.println("✅ VERIFIED: U2 IS PLAYABLE!");
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
