import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyPipedVideo {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            System.out.println("Testing piped.video for: " + videoId);
            // Piped.video usually has a streams endpoint or uses a specific API
            URL url = new URL("https://pipedapi.kavin.rocks/streams/" + videoId); // Try Kavin again but /streams
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            int code = conn.getResponseCode();
            System.out.println("Response: " + code);
            if (code == 200) {
                System.out.println("✅ SUCCESS: Kavin /streams is UP.");
                return;
            }
            
            // Try another one
            url = new URL("https://pipedapi.drgns.space/streams/" + videoId);
            conn = (HttpURLConnection) url.openConnection();
            System.out.println("Drgns Response: " + conn.getResponseCode());
            
        } catch (Exception e) { e.printStackTrace(); }
    }
}
