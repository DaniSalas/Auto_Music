import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestNewpipeLibTest {
    public static void main(String[] args) {
        String[] urls = {
            "https://pipedapi.drgns.space/streams/dQw4w9WgXcQ",
            "https://pipedapi.kavin.rocks/streams/dQw4w9WgXcQ",
            "https://pipedapi.adminforge.de/streams/dQw4w9WgXcQ"
        };
        for (String u : urls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
                conn.setInstanceFollowRedirects(false);
                System.out.println(u + " -> Code: " + conn.getResponseCode() + ", Location: " + conn.getHeaderField("Location"));
            } catch (Exception e) {
                System.out.println(u + " -> Error: " + e.getMessage());
            }
        }
    }
}
