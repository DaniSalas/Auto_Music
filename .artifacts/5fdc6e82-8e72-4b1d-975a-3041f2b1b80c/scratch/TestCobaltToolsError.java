import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestCobaltToolsError {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://api.cobalt.tools/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String body = "{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\",\"audioFormat\":\"mp3\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("utf-8"));
            }
            
            System.out.println("Code: " + conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            System.out.println("Error/Response: " + sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
