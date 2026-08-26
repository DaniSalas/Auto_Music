import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInnertube {
    public static void main(String[] args) {
        String videoId = "jNQXAC9IVRw"; // Me at the zoo
        try {
            System.out.println("Checking standard video: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                if (json.contains("\"status\":\"OK\"") && json.contains("\"url\":\"https://")) {
                    System.out.println("✅ SUCCESS: Found direct URL!");
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
