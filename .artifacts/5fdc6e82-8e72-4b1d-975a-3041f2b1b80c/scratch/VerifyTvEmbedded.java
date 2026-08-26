import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTvEmbedded {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing TVHTML5_SIMPLY_EMBEDDED_PLAYER for U2...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/605.1.15 (KHTML, like Gecko) SamsungBrowser/5.0 TV Safari/605.1.15");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\",\"hl\":\"en\",\"gl\":\"US\"},\"thirdParty\":{\"embedUrl\":\"https://www.youtube.com/embed/" + videoId + "\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("\"url\":\"https://")) {
                        System.out.println("✅ FOUND DIRECT URL!");
                        return;
                    }
                }
                System.out.println("❌ No direct URL found.");
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
