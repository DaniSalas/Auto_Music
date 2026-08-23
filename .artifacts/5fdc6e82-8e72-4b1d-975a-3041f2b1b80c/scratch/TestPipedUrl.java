import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestPipedUrl {
    public static void main(String[] args) {
        try {
            String target = "https://piped.video/api/v1/videos/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            int idx = json.indexOf("\"url\":\"");
            if (idx != -1) {
                int endIdx = json.indexOf("\"", idx + 7);
                String url = json.substring(idx + 7, endIdx);
                System.out.println("EXTRACTED URL: " + url);
                
                // Test URL accessibility
                HttpURLConnection audioConn = (HttpURLConnection) new URL(url).openConnection();
                audioConn.setRequestMethod("HEAD");
                System.out.println("Audio URL Response Code: " + audioConn.getResponseCode());
            } else {
                System.out.println("URL not found in JSON");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
