import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTvFinal {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Final TV identity check for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/6.0 TV Safari/605.1.15");
            conn.setRequestProperty("Referer", "https://www.youtube.com/embed/" + videoId);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ TV Status OK!");
                    if (line.contains("\"url\":\"https://")) {
                        System.out.println("⭐⭐⭐ SUCCESS! DIRECT URL FOUND! ⭐⭐⭐");
                    } else {
                        System.out.println("🔒 Still ciphered. Trying ANDROID identity...");
                        tryAndroid(videoId);
                    }
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void tryAndroid(String videoId) throws Exception {
        URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.34.35 (Linux; U; Android 14)");
        conn.setDoOutput(true);
        String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID\",\"clientVersion\":\"19.34.35\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
        try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
        if (conn.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = in.readLine();
            if (line.contains("\"url\":\"https://")) {
                System.out.println("  ✅ ANDROID SUCCESS!");
            } else {
                System.out.println("  ❌ ANDROID FAIL.");
            }
        }
    }
}
