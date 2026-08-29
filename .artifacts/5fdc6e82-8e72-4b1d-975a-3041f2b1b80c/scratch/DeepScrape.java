import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeepScrape {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Deep Scraping for: " + videoId);
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("hlsManifestUrl")) {
                        int start = line.indexOf("hlsManifestUrl\":\"") + 17;
                        int end = line.indexOf("\"", start);
                        String hls = line.substring(start, end).replace("\\u0026", "&");
                        System.out.println("✅ FOUND HLS: " + hls);
                        return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("  ❌ FAILED.");
    }
}
