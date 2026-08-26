import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTv3 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing TVHTML5_SIMPLY_EMBEDDED_PLAYER v3.0...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/605.1.15 (KHTML, like Gecko) SamsungBrowser/5.0 TV Safari/605.1.15");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"3.0\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                if (response.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ Status OK!");
                    if (response.contains("\"url\":\"https://")) {
                        System.out.println("✅ DIRECT URL FOUND!");
                    } else if (response.contains("signatureCipher")) {
                        System.out.println("🔒 Ciphered.");
                    }
                }
            } else {
                 System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
