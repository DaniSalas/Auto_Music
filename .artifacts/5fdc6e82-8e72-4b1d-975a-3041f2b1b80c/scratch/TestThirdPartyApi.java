import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestThirdPartyApi {
    public static void main(String[] args) {
        String[] endpoints = {
            "https://pipedapi.kavin.rocks/api/v1/videos/dQw4w9WgXcQ",
            "https://api.piped.privacy.com.de/api/v1/videos/dQw4w9WgXcQ",
            "https://pipedapi.drgns.space/api/v1/videos/dQw4w9WgXcQ"
        };
        for (String ep : endpoints) {
            try {
                System.out.println("Testing: " + ep);
                HttpURLConnection conn = (HttpURLConnection) new URL(ep).openConnection();
                conn.setConnectTimeout(3000);
                System.out.println("Code: " + conn.getResponseCode());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
