import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MultiClientVerify {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        
        String[] clients = {"ANDROID_MUSIC", "ANDROID_TESTSUITE", "ANDROID_VR", "WEB_REMIX", "IOS", "TVHTML5_SIMPLY_EMBEDDED_PLAYER"};
        String[] versions = {"7.01.52", "1.9", "1.61.48", "1.20240522.01.00", "19.29.1", "2.0"};
        
        for (int i = 0; i < clients.length; i++) {
            testClient(videoId, clients[i], versions[i]);
        }
    }

    private static void testClient(String videoId, String clientName, String clientVersion) {
        try {
            System.out.println("Testing Client: " + clientName);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"" + clientName + "\",\"clientVersion\":\"" + clientVersion + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.println("  ✅ STATUS OK");
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("  🔓 FOUND DIRECT URL!");
                        return;
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("  🔒 CONTAINS CIPHER (Needs decryption)");
                    }
                } else {
                    System.out.println("  ❌ STATUS: " + (json.contains("reason") ? "FAIL" : "UNKNOWN"));
                }
            } else {
                System.out.println("  ❌ HTTP " + code);
            }
        } catch (Exception e) {
            System.out.println("  ❌ ERROR: " + e.getMessage());
        }
    }
}
