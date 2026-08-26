import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyCobaltCanine {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            System.out.println("Testing cobalt.canine.tools for: " + videoId);
            URL url = new URL("https://cobalt.canine.tools/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);

            String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            System.out.println("Response Code: " + code);
            if (code == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                System.out.println("✅ SUCCESS: " + sb.toString());
                return;
            } else {
                System.out.println("Failed with code: " + code);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
