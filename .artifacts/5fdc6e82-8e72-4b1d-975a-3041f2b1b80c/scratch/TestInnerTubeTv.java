import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestInnerTubeTv {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"dQw4w9WgXcQ\"}";
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
            int statusIdx = json.indexOf("status\":\"");
            if (statusIdx != -1) {
                System.out.println("Status value: " + json.substring(statusIdx, statusIdx + 30));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
