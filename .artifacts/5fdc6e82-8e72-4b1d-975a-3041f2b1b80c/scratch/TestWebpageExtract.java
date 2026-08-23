import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestWebpageExtract {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String html = sb.toString();
            int idx = html.indexOf("ytInitialPlayerResponse = ");
            if (idx != -1) {
                int endIdx = html.indexOf(";</script>", idx);
                String json = html.substring(idx + 26, endIdx);
                int urlIdx = json.indexOf("\"url\":\"");
                if (urlIdx != -1) {
                    int urlEnd = json.indexOf("\"", urlIdx + 7);
                    String streamUrl = json.substring(urlIdx + 7, urlEnd);
                    System.out.println("EXTRACTED STREAM URL VIA WEB SCRAPE: " + streamUrl);
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                    audioConn.setRequestMethod("HEAD");
                    System.out.println("Audio Response Code: " + audioConn.getResponseCode());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
