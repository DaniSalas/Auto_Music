import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MobileScrape {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Scraping Mobile YouTube for: " + videoId);
            URL url = new URL("https://m.youtube.com/watch?v=" + videoId);
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
                            if (end == -1) end = line.indexOf("\\\"", idx);
                            if (end != -1) {
                                String streamUrl = line.substring(idx, end).replace("\\u0026", "&");
                                if (streamUrl.contains("googlevideo.com") && streamUrl.contains("itag=140")) {
                                    System.out.println("✅ FOUND STREAM URL!");
                                    return;
                                }
                            }
                            idx = line.indexOf("https://", idx + 1);
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
