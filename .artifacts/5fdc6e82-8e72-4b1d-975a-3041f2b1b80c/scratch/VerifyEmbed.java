import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyEmbed {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing embed page for: " + videoId);
            URL url = new URL("https://www.youtube.com/embed/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("ytInitialPlayerResponse")) {
                        System.out.println("✅ Found player response in embed!");
                        if (line.contains("googlevideo.com")) {
                            System.out.println("✅ Found streams in embed!");
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ Embed extraction failed.");
    }
}
