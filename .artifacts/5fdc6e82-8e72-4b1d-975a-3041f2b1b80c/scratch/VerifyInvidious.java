import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInvidious {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            System.out.println("Testing invidious.io.lol...");
            URL url = new URL("https://invidious.io.lol/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                System.out.println("JSON received.");
                if (json.contains("adaptiveFormats")) {
                    System.out.println("✅ Found adaptiveFormats.");
                    // Extract first audio url
                    int start = json.indexOf("\"url\":\"") + 7;
                    int end = json.indexOf("\"", start);
                    String audioUrl = json.substring(start, end);
                    System.out.println("Checking audio URL: " + audioUrl.substring(0, 80) + "...");
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(audioUrl).openConnection();
                    audioConn.setRequestMethod("GET");
                    audioConn.setRequestProperty("Range", "bytes=0-1024");
                    int code = audioConn.getResponseCode();
                    System.out.println("Audio Response Code: " + code);
                    if (code == 200 || code == 206) {
                        System.out.println("⭐⭐⭐ SUCCESS! ⭐⭐⭐");
                        return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
