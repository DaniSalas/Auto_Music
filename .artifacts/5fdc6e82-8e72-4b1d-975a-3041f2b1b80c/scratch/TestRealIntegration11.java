import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestRealIntegration11 {
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
                    int section = json.indexOf("{", itagIdx + 10);
                    // let's find all urls after itag 140
                    int curr = json.indexOf("\"url\":\"", itagIdx);
                    while (curr != -1) {
                        int urlEnd = json.indexOf("\"", curr + 7);
                        String streamUrl = json.substring(curr + 7, urlEnd).replace("\\u0026", "&");
                        if (streamUrl.contains("googlevideo.com") && !streamUrl.contains("initplayback")) {
                            System.out.println("AUDIO STREAM URL: " + streamUrl);
                            HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                            audioConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                            audioConn.setRequestMethod("HEAD");
                            System.out.println("Audio Response Code: " + audioConn.getResponseCode());
                            return;
                        }
                        curr = json.indexOf("\"url\":\"", curr + 1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
