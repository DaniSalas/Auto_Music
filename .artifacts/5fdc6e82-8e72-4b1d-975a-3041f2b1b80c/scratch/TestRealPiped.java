import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestRealPiped {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.privacy.com.de",
            "https://pipedapi.astrid.sh"
        };
        for (String api : apis) {
            try {
                String target = api + "/api/v1/videos/dQw4w9WgXcQ";
                System.out.println("Trying API: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    System.out.println("JSON length: " + json.length());
                    if (json.contains("audioStreams")) {
                        System.out.println("SUCCESS with " + api);
                        int idx = json.indexOf("audioStreams");
                        System.out.println("Snippet: " + json.substring(idx, Math.min(json.length(), idx + 300)));
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }
    }
}
