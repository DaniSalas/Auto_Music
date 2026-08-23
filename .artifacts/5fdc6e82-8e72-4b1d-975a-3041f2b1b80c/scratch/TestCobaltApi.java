import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestCobaltApi {
    public static void main(String[] args) {
        String[] cobaltInstances = {
            "https://co.wuk.sh/api/json",
            "https://cobalt.projectsegfau.lt/api/json",
            "https://api.cobalt.tools/api/json"
        };
        for (String inst : cobaltInstances) {
            try {
                System.out.println("Testing Cobalt: " + inst);
                URL url = new URL(inst);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setDoOutput(true);
                
                String body = "{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\",\"audioFormat\":\"mp3\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("utf-8"));
                }
                
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    System.out.println("Response: " + sb.toString());
                    return;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
