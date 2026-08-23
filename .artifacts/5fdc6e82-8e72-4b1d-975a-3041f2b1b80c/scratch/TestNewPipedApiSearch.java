import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNewPipedApiSearch {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.drgns.space",
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacy.com.de"
        };
        for (String api : apis) {
            try {
                String target = api + "/streams/dQw4w9WgXcQ";
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    System.out.println("JSON length: " + json.length());
                    int urlIdx = json.indexOf("\"url\":\"");
                    if (urlIdx != -1) {
                        int urlEnd = json.indexOf("\"", urlIdx + 7);
                        String u = json.substring(urlIdx + 7, urlEnd);
                        System.out.println("FOUND STREAM URL: " + u);
                        
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(u).openConnection();
                        audioConn.setRequestMethod("HEAD");
                        System.out.println("Audio Head Response: " + audioConn.getResponseCode());
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
