import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyTie {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M"; 
        String instance = "https://invidious.tiekoetter.com";
        try {
            System.out.println("Testing " + instance);
            URL url = new URL(instance + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                if (json.contains("adaptiveFormats")) {
                    int start = json.indexOf("\"url\":\"") + 7;
                    int end = json.indexOf("\"", start);
                    String audioUrl = json.substring(start, end);
                    System.out.println("Extracted URL: " + audioUrl.substring(0, 100) + "...");
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(audioUrl).openConnection();
                    audioConn.setRequestMethod("GET");
                    audioConn.setRequestProperty("Range", "bytes=0-1024");
                    int code = audioConn.getResponseCode();
                    System.out.println("Audio Response: " + code);
                    if (code == 200 || code == 206) {
                        System.out.println("⭐⭐⭐ SUCCESS! tiekoetter works! ⭐⭐⭐");
                        return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
