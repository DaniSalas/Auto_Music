import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyPiped {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] instances = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.privacy.com.de"
        };

        for (String instance : instances) {
            try {
                System.out.println("Testing instance: " + instance);
                URL url = new URL(instance + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                System.out.println("Response Code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    String json = content.toString();
                    if (json.contains("audioStreams")) {
                        System.out.println("SUCCESS: Instance " + instance + " returned audio streams.");
                        // Extract first URL (very crude way)
                        int start = json.indexOf("\"url\":\"") + 7;
                        int end = json.indexOf("\"", start);
                        String audioUrl = json.substring(start, end);
                        System.out.println("Extracted Audio URL: " + audioUrl);
                        
                        // Verify audio URL
                        System.out.println("Verifying audio URL accessibility...");
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(audioUrl).openConnection();
                        audioConn.setRequestMethod("HEAD");
                        int audioCode = audioConn.getResponseCode();
                        System.out.println("Audio URL HEAD Response: " + audioCode);
                        if (audioCode == 200 || audioCode == 302) {
                            System.out.println("VERIFICATION COMPLETE: This instance works!");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error with instance " + instance + ": " + e.getMessage());
            }
        }
        System.out.println("FAILURE: No working Piped instances found.");
    }
}
