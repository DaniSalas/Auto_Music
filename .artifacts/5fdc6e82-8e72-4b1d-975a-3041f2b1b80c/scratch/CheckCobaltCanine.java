import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckCobaltCanine {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://cobalt.canine.tools/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));
            if (conn.getResponseCode() == 200) {
                 System.out.println("✅ SUCCESS: " + new BufferedReader(new InputStreamReader(conn.getInputStream())).readLine());
            } else {
                 System.out.println("❌ FAILED: " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
