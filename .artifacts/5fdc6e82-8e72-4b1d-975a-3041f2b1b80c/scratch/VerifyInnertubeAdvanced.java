import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInnertubeAdvanced {
    public static void main(String[] args) {
        String u2 = "uzF0M-9fO_M";
        String rick = "dQw4w9WgXcQ";
        
        System.out.println("--- TESTING U2 (Protected) ---");
        testAllClients(u2);
        
        System.out.println("\n--- TESTING Rick Astley (Standard) ---");
        testAllClients(rick);
    }

    private static void testAllClients(String videoId) {
        String[] clientNames = {"WEB_REMIX", "ANDROID_MUSIC", "ANDROID_VR", "TVHTML5_SIMPLY_EMBEDDED_PLAYER"};
        String[] clientVersions = {"1.20240522.01.00", "7.01.52", "1.61.48", "2.0"};
        String[] uas = {
            "Mozilla/5.0",
            "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14)",
            "com.google.android.apps.youtube.vr.oculus/1.61.48",
            "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0)"
        };

        for (int i = 0; i < clientNames.length; i++) {
            testClient(videoId, clientNames[i], clientVersions[i], uas[i]);
        }
    }

    private static void testClient(String videoId, String cName, String cVer, String ua) {
        try {
            System.out.print("Client " + cName + ": ");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", ua);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"" + cName + "\",\"clientVersion\":\"" + cVer + "\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.print("OK. ");
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("✅ DIRECT URL FOUND!");
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("🔒 CIPHERED.");
                    } else {
                        System.out.println("❓ Status OK but no stream found.");
                    }
                } else {
                    int sIdx = json.indexOf("\"status\":\"");
                    if (sIdx != -1) {
                        System.out.println("❌ " + json.substring(sIdx + 10, json.indexOf("\"", sIdx + 10)));
                    } else {
                        System.out.println("❌ UNKNOWN ERROR");
                    }
                }
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { System.out.println("❌ ERROR: " + e.getMessage()); }
    }
}
