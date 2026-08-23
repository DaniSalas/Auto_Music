import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestStreamExtractor {
    public static void main(String[] args) {
        String[] urls = {
            "https://pipedapi.kavin.rocks/api/v1/streams/dQw4w9WgXcQ",
            "https://vid.puffyan.us/api/v1/streams/dQw4w9WgXcQ",
            "https://pipedapi.drgns.space/api/v1/streams/dQw4w9WgXcQ"
        };
        for (String u : urls) {
            try {
                System.out.println("Testing: " + u);
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                conn.setConnectTimeout(4000);
                System.out.println("Code: " + conn.getResponseCode());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
