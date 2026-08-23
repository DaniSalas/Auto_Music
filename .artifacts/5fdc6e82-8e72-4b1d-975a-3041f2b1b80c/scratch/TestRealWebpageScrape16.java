import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestRealWebpageScrape16 {
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
                int itagIdx = json.indexOf("\"itag\":140");
                if (itagIdx != -1) {
                    // find the *previous* occurrence of "url":" before itagIdx
                    int urlIdx = json.lastIndexOf("\"url\":\"", itagIdx);
                    // wait, let's find all occurrences of "url":" and pick the one right before itagIdx (closest preceding)
                    int lastUrlIdx = -1;
                    int curr = json.indexOf("\"url\":\"");
                    while (curr != -1 && curr < itagIdx) {
                        lastUrlIdx = curr;
                        curr = json.indexOf("\"url\":\"", curr + 1);
                    }
                    System.out.println("lastUrlIdx before itag 140: " + lastUrlIdx);
                    if (lastUrlIdx != -1) {
                        int urlEnd = json.indexOf("\"", lastUrlIdx + 7);
                        String streamUrl = json.substring(lastUrlIdx + 7, urlEnd).replace("\\u0026", "&");
                        System.out.println("TRUE AUDIO STREAM URL: " + streamUrl);
                        
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                        audioConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        audioConn.setRequestMethod("HEAD");
                        System.out.println("Audio Stream Response Code: " + audioConn.getResponseCode());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
