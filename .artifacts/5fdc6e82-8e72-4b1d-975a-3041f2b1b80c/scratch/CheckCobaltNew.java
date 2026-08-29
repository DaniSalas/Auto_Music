import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckCobaltNew {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {
            "https://cobalt.meowing.de",
            "https://cobalt.canine.tools",
            "https://api.cobalt.tools"
        };
        for (String base : pool) {
            try {
                System.out.println("Testing: " + base);
                URL url = new URL(base);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setDoOutput(true);
                
                String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
                conn.getOutputStream().write(payload.getBytes("UTF-8"));
                
                int code = conn.getResponseCode();
                System.out.println("  Code: " + code);
                if (code == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    System.out.println("  ✅ SUCCESS: " + in.readLine());
                } else if (code == 405) {
                    System.out.println("  ❌ 405: Needs /api/json?");
                    testV7(base, videoId);
                }
            } catch (Exception e) { System.out.println("  ❌ Error: " + e.getMessage()); }
        }
    }

    private static void testV7(String base, String videoId) {
        try {
            URL url = new URL(base + "/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));
            if (conn.getResponseCode() == 200) {
                 System.out.println("  ✅ V7 SUCCESS!");
            }
        } catch (Exception e) {}
    }
}
