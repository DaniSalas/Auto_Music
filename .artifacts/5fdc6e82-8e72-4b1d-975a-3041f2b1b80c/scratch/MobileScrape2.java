import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MobileScrape2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Scraping Mobile YouTube with Android UA...");
            URL url = new URL("https://m.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("googlevideo.com/videoplayback")) {
                         System.out.println("✅ FOUND GOOGLEVIDEO!");
                         return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
