import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyMeow {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing cobalt.meowing.de API...");
            URL url = new URL("https://cobalt.meowing.de/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            int code = conn.getResponseCode();
            System.out.println("HTTP Code: " + code);
            if (code == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String json = in.readLine();
                System.out.println("✅ SUCCESS! " + json);
                if (json.contains("\"url\":\"https://")) {
                     System.out.println("⭐⭐⭐ WORKING COBALT INSTANCE FOUND! ⭐⭐⭐");
                }
            } else {
                System.out.println("❌ Failed.");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
