import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RobustScrape {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Robust Scraping for: " + videoId);
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // Desktop UA
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String html = sb.toString();
                
                // Find ytInitialPlayerResponse
                int startIdx = html.indexOf("ytInitialPlayerResponse = ");
                if (startIdx != -1) {
                    int endIdx = html.indexOf(";</script>", startIdx);
                    String json = html.substring(startIdx + 26, endIdx);
                    System.out.println("JSON found.");
                    
                    // Regex-like search for itag 140 URL
                    int itagIdx = json.indexOf("\"itag\":140");
                    if (itagIdx != -1) {
                         int urlIdx = json.indexOf("\"url\":\"", itagIdx);
                         if (urlIdx != -1 && (urlIdx - itagIdx < 2000)) {
                             int uEnd = json.indexOf("\"", urlIdx + 7);
                             String streamUrl = json.substring(urlIdx + 7, uEnd).replace("\\u0026", "&");
                             System.out.println("✅ EXTRACTED URL: " + streamUrl.substring(0, 100) + "...");
                             verify(streamUrl);
                             return;
                         } else {
                             System.out.println("URL not in itag block. Checking signatureCipher...");
                             if (json.contains("signatureCipher")) {
                                 System.out.println("🔒 Ciphered.");
                             }
                         }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }

    private static void verify(String u) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            int code = conn.getResponseCode();
            System.out.println("  Verify Response: " + code);
            if (code == 200 || code == 206) System.out.println("  ⭐⭐⭐ SUCCESS! ⭐⭐⭐");
        } catch (Exception e) { e.printStackTrace(); }
    }
}
