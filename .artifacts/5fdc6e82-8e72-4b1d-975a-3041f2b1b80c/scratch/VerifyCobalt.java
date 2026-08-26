import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyCobalt {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing Cobalt for: " + videoId);
            URL url = new URL("https://api.cobalt.tools/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            System.out.println("Response Code: " + code);
            if (code == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                System.out.println("✅ SUCCESS: " + response);
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                System.out.println("❌ ERROR: " + in.readLine());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
