import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyNewStrategies {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        
        // Strategy A: WEB_EMBEDDED_PLAYER with Referer
        testIdentity(videoId, "WEB_EMBEDDED_PLAYER", "1.20240826.01.00", "https://www.youtube.com/embed/" + videoId);
        
        // Strategy B: MWEB
        testIdentity(videoId, "MWEB", "2.20240826.01.00", "https://m.youtube.com/watch?v=" + videoId);
        
        // Strategy C: Check a few fresh Invidious instances
        String[] fresh = {"https://inv.vern.cc", "https://invidious.asir.dev", "https://iv.n8pjl.ca"};
        for (String inst : fresh) {
            testInvidious(inst, videoId);
        }
    }

    private static void testIdentity(String videoId, String cName, String cVer, String referer) {
        try {
            System.out.println("Testing Identity: " + cName);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", referer);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"" + cName + "\",\"clientVersion\":\"" + cVer + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line.contains("\"url\":\"https://")) {
                    System.out.println("  ✅ SUCCESS: Direct URL!");
                } else if (line.contains("signatureCipher")) {
                    System.out.println("  🔒 Ciphered.");
                } else {
                    System.out.println("  ❌ Restricted/Login required.");
                }
            } else {
                System.out.println("  ❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void testInvidious(String inst, String videoId) {
        try {
            System.out.println("Testing Invidious: " + inst);
            URL url = new URL(inst + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line.contains("audioStreams")) System.out.println("  ✅ Streams found!");
            } else {
                System.out.println("  ❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { System.out.println("  ❌ " + e.getMessage()); }
    }
}
