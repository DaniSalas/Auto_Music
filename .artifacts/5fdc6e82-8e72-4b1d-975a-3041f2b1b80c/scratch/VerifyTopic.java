import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTopic {
    public static void main(String[] args) {
        String videoId = "ifKu1Psy6M4"; // U2 - With or Without You (Topic)
        try {
            System.out.println("Testing Topic Video: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                if (response.contains("\"url\":\"https://")) {
                    System.out.println("✅ SUCCESS! Topic version has a direct URL!");
                    int start = response.indexOf("\"url\":\"https://") + 7;
                    int end = response.indexOf("\"", start);
                    String streamUrl = response.substring(start, end).replace("\\u0026", "&");
                    verify(streamUrl);
                } else {
                    System.out.println("❌ No direct URL for Topic version.");
                }
            } else {
                 System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void verify(String u) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                System.out.println("⭐⭐⭐ VERIFIED: TOPIC VERSION IS PLAYABLE! ⭐⭐⭐");
            }
        } catch (Exception e) {}
    }
}
