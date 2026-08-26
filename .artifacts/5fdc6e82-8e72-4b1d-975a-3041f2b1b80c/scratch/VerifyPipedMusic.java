import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyPipedMusic {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M"; // U2 - With or Without You
        String[] apis = {
            "https://pipedapi.mha.fi",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space"
        };

        for (String api : apis) {
            try {
                System.out.println("Testing API: " + api);
                URL url = new URL(api + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    String json = sb.toString();
                    if (json.contains("audioStreams")) {
                        System.out.println("✅ SUCCESS: Found audio streams on " + api);
                        int start = json.indexOf("\"url\":\"") + 7;
                        int end = json.indexOf("\"", start);
                        String streamUrl = json.substring(start, end);
                        
                        System.out.println("Verifying stream URL...");
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                        audioConn.setRequestMethod("GET");
                        audioConn.setRequestProperty("Range", "bytes=0-1024");
                        int code = audioConn.getResponseCode();
                        System.out.println("Stream Response: " + code);
                        if (code == 200 || code == 206) {
                            System.out.println("⭐⭐⭐ " + api + " IS FULLY FUNCTIONAL FOR MUSIC! ⭐⭐⭐");
                            return;
                        }
                    }
                }
            } catch (Exception e) { System.out.println("API " + api + " failed."); }
        }
        System.out.println("❌ No Piped instances found working for music.");
    }
}
