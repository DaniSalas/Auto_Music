import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestCobalt {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://co.wuk.sh/api/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            
            String body = "{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\",\"audioFormat\":\"mp3\"}";
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int code = conn.getResponseCode();
            System.out.println("Cobalt Code: " + code);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            System.out.println("Cobalt Response: " + sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
