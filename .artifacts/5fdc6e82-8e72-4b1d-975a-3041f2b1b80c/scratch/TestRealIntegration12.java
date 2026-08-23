import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestRealIntegration12 {
    public static void main(String[] args) {
        try {
            String videoId = "dQw4w9WgXcQ";
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
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
                
                int itagIdx = json.indexOf("\"itag\":140");
                if (itagIdx != -1) {
                    int urlIdx = json.indexOf("\"url\":\"", itagIdx);
                    System.out.println("urlIdx after itag 140: " + urlIdx);
                    if (urlIdx != -1) {
                        int urlEnd = json.indexOf("\"", urlIdx + 7);
                        String streamUrl = json.substring(urlIdx + 7, urlEnd).replace("\\u0026", "&");
                        System.out.println("URL: " + streamUrl);
                        
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                        audioConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        audioConn.setRequestMethod("HEAD");
                        System.out.println("Response Code: " + audioConn.getResponseCode());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
