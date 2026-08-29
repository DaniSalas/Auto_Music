import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTv2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing TVHTML5 Identity for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/605.1.15 (KHTML, like Gecko) SamsungBrowser/5.0 TV Safari/605.1.15");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            conn.getOutputStream().write(payload.getBytes("UTF-8"));

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                String json = sb.toString();
                System.out.println("Response: " + json.substring(0, Math.min(json.length(), 200)));
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ Status OK!");
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("🔓 DIRECT URL FOUND!");
                    } else {
                        System.out.println("🔒 Ciphered.");
                    }
                } else if (json.contains("\"status\":\"")) {
                     int idx = json.indexOf("\"status\":\"");
                     System.out.println("❌ Status: " + json.substring(idx + 10, json.indexOf("\"", idx + 10)));
                }
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
