import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInnertubeNew {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing ANDROID client 19.34.35 for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.34.35 (Linux; U; Android 14; en_US; Pixel 8 Pro) gzip");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\"19.34.35\",\"hl\":\"en\",\"gl\":\"US\",\"osName\":\"Android\",\"osVersion\":\"14\",\"androidSdkVersion\":34}},\"videoId\":\"" + videoId + "\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20626}}}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ Status: OK");
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("✅ SUCCESS: Direct URL found!");
                        return;
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("🔒 Ciphered response.");
                        int start = json.indexOf("signatureCipher\":\"") + 18;
                        int end = json.indexOf("\"", start);
                        System.out.println("Cipher: " + json.substring(start, Math.min(start + 100, end)) + "...");
                    }
                } else {
                    System.out.println("❌ Status: " + json.substring(json.indexOf("\"status\":\"") + 10, json.indexOf("\"", json.indexOf("\"status\":\"") + 10)));
                }
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
