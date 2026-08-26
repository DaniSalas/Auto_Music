import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyCobaltFinal {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://cobalt.meowing.de/",
            "https://cobalt.canine.tools/",
            "https://cobalt.sh/",
            "https://api.cobalt.tools/"
        };

        for (String inst : instances) {
            try {
                System.out.println("Testing Cobalt: " + inst);
                URL url = new URL(inst);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
                conn.setDoOutput(true);

                String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\",\"audioFormat\":\"mp3\"}";
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

                int code = conn.getResponseCode();
                System.out.println("  HTTP Code: " + code);
                if (code == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String json = in.readLine();
                    System.out.println("  ✅ SUCCESS: " + json);
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("⭐⭐⭐ FOUND WORKING COBALT INSTANCE! ⭐⭐⭐");
                        return;
                    }
                } else if (code >= 400) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    System.out.println("  ❌ Error Body: " + in.readLine());
                }
            } catch (Exception e) {
                System.out.println("  ❌ Error: " + e.getMessage());
            }
        }
    }
}
