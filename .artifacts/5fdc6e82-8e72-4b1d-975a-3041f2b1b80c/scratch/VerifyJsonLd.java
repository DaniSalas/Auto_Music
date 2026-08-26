import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyJsonLd {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String html = sb.toString();
            
            int gvIdx = html.indexOf("googlevideo.com/videoplayback");
            if (gvIdx != -1) {
                int start = html.lastIndexOf("\"url\":\"", gvIdx) + 7;
                int end = html.indexOf("\"", start);
                String streamUrl = html.substring(start, end).replace("\\u0026", "&");
                System.out.println("✅ EXTRACTED URL: " + streamUrl.substring(0, 100) + "...");
                
                HttpURLConnection test = (HttpURLConnection) new URL(streamUrl).openConnection();
                test.setRequestProperty("User-Agent", "Mozilla/5.0");
                test.setRequestMethod("GET");
                test.setRequestProperty("Range", "bytes=0-1024");
                int code = test.getResponseCode();
                System.out.println("Stream Response Code: " + code);
                if (code == 200 || code == 206) {
                    System.out.println("⭐⭐⭐ VERIFIED: U2 IS PLAYABLE VIA MOBILE SCRAPE! ⭐⭐⭐");
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
