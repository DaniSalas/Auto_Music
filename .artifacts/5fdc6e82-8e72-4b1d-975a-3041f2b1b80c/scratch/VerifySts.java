import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifySts {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing ANDROID client with STS 20684...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.34.35 (Linux; U; Android 14)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\"19.34.35\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20684}}}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                 System.out.println("✅ OK!");
            } else {
                System.out.println("❌ " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
