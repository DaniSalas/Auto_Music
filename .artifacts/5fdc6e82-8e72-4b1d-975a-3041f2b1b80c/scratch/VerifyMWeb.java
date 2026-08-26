import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyMWeb {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing MWEB identity for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("X-YouTube-Client-Name", "2");
            conn.setRequestProperty("X-YouTube-Client-Version", "2.20240821.01.00");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"MWEB\",\"clientVersion\":\"2.20240821.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
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
                        System.out.println("⭐⭐⭐ SUCCESS! DIRECT URL FOUND! ⭐⭐⭐");
                        return;
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("🔒 Ciphered.");
                    }
                } else {
                    System.out.println("❌ Status: NOT OK");
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
