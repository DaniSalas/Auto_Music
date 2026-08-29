import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyAnyWorking {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ"; // Rick Astley
        // Try Piped official
        try {
            System.out.println("Testing Piped.video...");
            URL url = new URL("https://pipedapi.kavin.rocks/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            System.out.println("  Code: " + conn.getResponseCode());
        } catch (Exception e) { System.out.println("  Err: " + e.getMessage()); }

        // Try Invidious official
        try {
            System.out.println("Testing yewtu.be...");
            URL url = new URL("https://yewtu.be/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            System.out.println("  Code: " + conn.getResponseCode());
        } catch (Exception e) { System.out.println("  Err: " + e.getMessage()); }
        
        // Try direct Innertube
        try {
            System.out.println("Testing direct Innertube...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String p = "{\"context\":{\"client\":{\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\"}},\"videoId\":\"" + videoId + "\"}";
            conn.getOutputStream().write(p.getBytes("UTF-8"));
            System.out.println("  Code: " + conn.getResponseCode());
            if (conn.getResponseCode() == 200) {
                 BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                 String line = in.readLine();
                 System.out.println("  ✅ SUCCESS! Status: " + line.contains("\"status\":\"OK\""));
            }
        } catch (Exception e) { System.out.println("  Err: " + e.getMessage()); }
    }
}
