import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyLeptons {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing leptons.xyz...");
            URL url = new URL("https://pipedapi.leptons.xyz/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                if (json.contains("audioStreams")) {
                    int urlIdx = json.indexOf("\"url\":\"https://");
                    int urlEnd = json.indexOf("\"", urlIdx + 7);
                    String audioUrl = json.substring(urlIdx + 7, urlEnd);
                    System.out.println("✅ Found Audio URL.");
                    
                    HttpURLConnection test = (HttpURLConnection) new URL(audioUrl).openConnection();
                    test.setRequestMethod("GET");
                    test.setRequestProperty("Range", "bytes=0-1024");
                    if (test.getResponseCode() == 200 || test.getResponseCode() == 206) {
                        System.out.println("⭐⭐⭐ SUCCESS! LEPTONS WORKS! ⭐⭐⭐");
                        return;
                    }
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
