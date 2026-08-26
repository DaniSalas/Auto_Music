import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyWebRemix {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing WEB_REMIX with Music Key for: " + videoId);
            // Music Key
            URL url = new URL("https://music.youtube.com/youtubei/v1/player?key=AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            conn.setRequestProperty("Origin", "https://music.youtube.com");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240522.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

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
                        System.out.println("⭐⭐⭐ SUCCESS! ⭐⭐⭐");
                        return;
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("🔒 Ciphered.");
                    }
                } else {
                    System.out.println("❌ Status NOT OK.");
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
