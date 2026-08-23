import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestInnerTubeJson {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20260213.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"dQw4w9WgXcQ\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            System.out.println("JSON length: " + json.length());
            System.out.println("Contains streamingData: " + json.contains("streamingData"));
            System.out.println("Contains playabilityStatus: " + json.contains("playabilityStatus"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
