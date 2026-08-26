import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FinalVerifySts {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing ANDROID_MUSIC with STS 20688...");
            URL url = new URL("https://music.youtube.com/youtubei/v1/player?key=AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_MUSIC\",\"clientVersion\":\"7.01.52\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20688}}}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ STS 20688 WORKS!");
                    if (line.contains("\"url\":\"https://")) {
                        System.out.println("⭐⭐⭐ DIRECT URL FOUND! ⭐⭐⭐");
                    } else {
                        System.out.println("🔒 Still ciphered, but status OK.");
                    }
                } else {
                    System.out.println("❌ FAILED: " + line.substring(0, Math.min(line.length(), 200)));
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
