import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNewpipeApi {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.kavin.rocks/streams/dQw4w9WgXcQ",
            "https://pipedapi.drgns.space/streams/dQw4w9WgXcQ"
        };
        for (String api : apis) {
            try {
                System.out.println("Testing: " + api);
                HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
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
                    if (json.contains("audioStreams")) {
                        System.out.println("SUCCESS!");
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
