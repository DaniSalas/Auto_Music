import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyIosNew {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing IOS 19.34.35 identity...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.ios.youtube/19.34.35 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"IOS\",\"clientVersion\":\"19.34.35\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20684}}}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                System.out.println("✅ Status: " + json.contains("\"status\":\"OK\""));
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
