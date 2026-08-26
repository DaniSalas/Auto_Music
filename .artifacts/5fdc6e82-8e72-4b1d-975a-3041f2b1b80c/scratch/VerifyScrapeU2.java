import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyScrapeU2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Deep Scraping for: " + videoId);
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            
            String html = sb.toString();
            // Look for any googlevideo.com link that contains itag=140
            int idx = html.indexOf("googlevideo.com");
            while (idx != -1) {
                int start = html.lastIndexOf("https://", idx);
                int end = html.indexOf("\"", idx);
                if (end == -1) end = html.indexOf("\\\"", idx);
                if (start != -1 && end != -1) {
                    String streamUrl = html.substring(start, end).replace("\\u0026", "&");
                    if (streamUrl.contains("itag=140")) {
                        System.out.println("✅ FOUND POTENTIAL AUDIO STREAM!");
                        HttpURLConnection test = (HttpURLConnection) new URL(streamUrl).openConnection();
                        test.setRequestProperty("User-Agent", "Mozilla/5.0");
                        if (test.getResponseCode() == 200 || test.getResponseCode() == 206) {
                            System.out.println("⭐⭐⭐ SUCCESS: U2 AUDIO STREAM IS LIVE! ⭐⭐⭐");
                            System.out.println("URL: " + streamUrl.substring(0, 80) + "...");
                            return;
                        }
                    }
                }
                idx = html.indexOf("googlevideo.com", idx + 1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ Deep scrape failed.");
    }
}
