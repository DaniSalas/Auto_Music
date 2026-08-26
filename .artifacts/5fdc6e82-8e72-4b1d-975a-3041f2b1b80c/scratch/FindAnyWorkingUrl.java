import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindAnyWorkingUrl {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ"; // Rick Astley
        try {
            // Strategy: Scraping with Mobile UA
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("googlevideo.com/videoplayback")) {
                        int idx = line.indexOf("https://");
                        while(idx != -1) {
                            int end = line.indexOf("\"", idx);
                            if (end != -1) {
                                String streamUrl = line.substring(idx, end).replace("\\u0026", "&");
                                if (streamUrl.contains("googlevideo.com") && streamUrl.contains("itag=140")) {
                                    System.out.println("✅ FOUND POTENTIAL URL: " + streamUrl);
                                    verify(streamUrl);
                                }
                            }
                            idx = line.indexOf("https://", idx + 1);
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void verify(String u) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            int code = conn.getResponseCode();
            System.out.println("  Verification Code: " + code);
            if (code == 200 || code == 206) System.out.println("  ⭐⭐⭐ SUCCESS! ⭐⭐⭐");
        } catch (Exception e) { System.out.println("  Failed: " + e.getMessage()); }
    }
}
