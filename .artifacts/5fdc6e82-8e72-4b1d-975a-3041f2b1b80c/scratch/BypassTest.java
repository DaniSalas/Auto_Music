import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BypassTest {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        
        // Identity 1: ANDROID_VR
        testIdentity(videoId, "ANDROID_VR", "1.61.48", "com.google.android.apps.youtube.vr.oculus/1.61.48");
        
        // Identity 2: ANDROID_TESTSUITE (Sometimes returns unencrypted URLs)
        testIdentity(videoId, "ANDROID_TESTSUITE", "1.15", "com.google.android.youtube/19.34.35");

        // Identity 3: WEB_EMBEDDED_PLAYER
        testIdentity(videoId, "WEB_EMBEDDED_PLAYER", "1.20240821.01.00", "Mozilla/5.0");
    }

    private static void testIdentity(String videoId, String clientName, String clientVersion, String ua) {
        try {
            System.out.println("Testing " + clientName + "...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", ua);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"" + clientName + "\",\"clientVersion\":\"" + clientVersion + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                boolean foundUrl = false;
                while ((line = in.readLine()) != null) {
                    if (line.contains("\"url\":\"https://")) {
                        System.out.println("  ✅ SUCCESS: Direct URL found!");
                        foundUrl = true;
                        break;
                    } else if (line.contains("signatureCipher")) {
                        System.out.println("  🔒 CIPHERED.");
                        foundUrl = true;
                        break;
                    }
                }
                if (!foundUrl) System.out.println("  ❌ No streaming data.");
            } else {
                System.out.println("  ❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
