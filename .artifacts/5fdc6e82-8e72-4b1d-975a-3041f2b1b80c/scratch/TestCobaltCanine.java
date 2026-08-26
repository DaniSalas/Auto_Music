import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestCobaltCanine {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://cobalt.canine.tools/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String body = "{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
            
            int code = conn.getResponseCode();
            System.out.println("Code: " + code);
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                System.out.println("Response: " + reader.readLine());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
