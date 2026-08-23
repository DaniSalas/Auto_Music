import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestDirectNewpipe {
    public static void main(String[] args) {
        String[] urls = {
            "https://pipedapi.drgns.space/api/v1/streams/dQw4w9WgXcQ",
            "https://pipedapi.kavin.rocks/api/v1/streams/dQw4w9WgXcQ"
        };
        for (String u : urls) {
            try {
                System.out.println("Testing: " + u);
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
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
                        System.out.println("SUCCESS URL: " + json.substring(urlIdx + 7, urlEnd));
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
