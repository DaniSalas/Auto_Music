import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TryCobaltFinal {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {
            "https://cobalt.meowing.de/",
            "https://cobalt.canine.tools/",
            "https://api.cobalt.tools/"
        };
        for (String inst : pool) {
            try {
                System.out.println("Testing Cobalt: " + inst);
                URL url = new URL(inst);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setDoOutput(true);
                
                String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
                conn.getOutputStream().write(payload.getBytes("UTF-8"));
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    System.out.println("  ✅ SUCCESS: " + in.readLine());
                } else {
                    System.out.println("  ❌ HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) { System.out.println("  ❌ " + e.getMessage()); }
        }
    }
}
