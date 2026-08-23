import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNewpipeBackend {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.privacy.com.de",
            "https://piped.video",
            "https://pipedapi.adminforge.de"
        };
        for (String api : apis) {
            try {
                String target = api + "/streams/dQw4w9WgXcQ";
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(4000);
                System.out.println("Code: " + conn.getResponseCode());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
