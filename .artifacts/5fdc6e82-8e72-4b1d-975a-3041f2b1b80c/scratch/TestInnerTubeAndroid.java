import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestInnerTubeAndroid {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.29.35 (Linux; U; Android 14; en_US; Pixel 7) gzip");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\"19.29.35\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"dQw4w9WgXcQ\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20492}}}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }
            
            int code = conn.getResponseCode();
            System.out.println("Android Code: " + code);
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
