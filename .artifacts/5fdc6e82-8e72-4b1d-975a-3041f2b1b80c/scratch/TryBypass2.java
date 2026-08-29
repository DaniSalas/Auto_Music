import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TryBypass2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        
        // Try ANDROID_TESTSUITE 1.9 (The "classic" bypass)
        test(videoId, "ANDROID_TESTSUITE", "1.9", "com.google.android.youtube/19.34.35");
        
        // Try ANDROID identity with old version
        test(videoId, "ANDROID", "17.31.35", "com.google.android.youtube/17.31.35 (Linux; U; Android 11)");
    }

    private static void test(String vId, String cName, String cVer, String ua) {
        try {
            System.out.println("Testing: " + cName + " " + cVer);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", ua);
            conn.setDoOutput(true);

            String p = "{\"context\":{\"client\":{\"clientName\":\"" + cName + "\",\"clientVersion\":\"" + cVer + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + vId + "\"}";
            conn.getOutputStream().write(p.getBytes("UTF-8"));

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line.contains("\"url\":\"https://")) System.out.println("  ✅ SUCCESS!");
                else if (line.contains("signatureCipher")) System.out.println("  🔒 Ciphered.");
                else System.out.println("  ❌ Blocked.");
            } else {
                System.out.println("  ❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
