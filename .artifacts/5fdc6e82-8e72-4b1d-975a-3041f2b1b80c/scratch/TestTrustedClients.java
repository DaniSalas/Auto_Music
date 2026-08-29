import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestTrustedClients {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        
        // 1. WEB_EMBEDDED_PLAYER
        test(videoId, "WEB_EMBEDDED_PLAYER", "1.20240826.01.00", "https://www.youtube.com/embed/" + videoId);
        
        // 2. ANDROID_TESTSUITE 1.9
        test(videoId, "ANDROID_TESTSUITE", "1.9", null);
        
        // 3. MWEB
        test(videoId, "MWEB", "2.20240826.01.00", "https://m.youtube.com/watch?v=" + videoId);
    }

    private static void test(String vId, String cName, String cVer, String referer) {
        try {
            System.out.println("Testing " + cName + "...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (referer != null) conn.setRequestProperty("Referer", referer);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"" + cName + "\",\"clientVersion\":\"" + cVer + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + vId + "\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean found = false;
                while ((line = in.readLine()) != null) {
                    if (line.contains("\"url\":\"https://")) {
                        System.out.println("  ✅ SUCCESS: Direct URL!");
                        found = true; break;
                    } else if (line.contains("signatureCipher")) {
                        System.out.println("  🔒 Ciphered.");
                        found = true; break;
                    }
                }
                if (!found) System.out.println("  ❌ No streamingData.");
            } else {
                System.out.println("  ❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
