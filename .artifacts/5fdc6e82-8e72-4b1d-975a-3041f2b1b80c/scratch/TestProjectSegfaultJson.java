import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestProjectSegfaultJson {
    public static void main(String[] args) {
        try {
            String target = "https://invidious.projectsegfau.lt/api/v1/videos/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            System.out.println("JSON length: " + json.length());
            System.out.println("JSON sample: " + json.substring(0, Math.min(json.length(), 1500)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
