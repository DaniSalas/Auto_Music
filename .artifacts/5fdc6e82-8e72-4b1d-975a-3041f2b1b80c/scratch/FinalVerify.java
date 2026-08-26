import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FinalVerify {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] instances = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.privacy.com.de"
        };
        
        for (String instance : instances) {
            try {
                System.out.println("Testing " + instance);
                URL url = new URL(instance + "/streams/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setConnectTimeout(5000);
                
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.contains("\"url\":\"https://")) {
                            int start = line.indexOf("\"url\":\"") + 7;
                            int end = line.indexOf("\"", start);
                            String streamUrl = line.substring(start, end);
                            if (streamUrl.contains("googlevideo.com")) {
                                System.out.println("✅ FOUND STREAM URL: " + streamUrl.substring(0, 100) + "...");
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
        System.out.println("❌ No stream found via Piped.");
    }
}
