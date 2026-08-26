import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyPipedStreamsU2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing Piped Streams API for: " + videoId);
            URL url = new URL("https://pipedapi.mha.fi/streams/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("\"url\":\"")) {
                        int start = line.indexOf("\"url\":\"") + 7;
                        int end = line.indexOf("\"", start);
                        String streamUrl = line.substring(start, end);
                        if (streamUrl.contains("googlevideo.com")) {
                            System.out.println("✅ SUCCESS: Found stream URL via /streams!");
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
