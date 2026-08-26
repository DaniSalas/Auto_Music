import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInvidiousAudio {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] instances = {
            "https://invidious.f5.si",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com"
        };
        
        for (String instance : instances) {
            try {
                System.out.println("Testing " + instance);
                // Use API to get video info
                URL url = new URL(instance + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = in.readLine()) != null) sb.append(line);
                    in.close();
                    
                    String json = sb.toString();
                    if (json.contains("adaptiveFormats")) {
                        // Extract first audio URL
                        int afIdx = json.indexOf("adaptiveFormats");
                        int urlIdx = json.indexOf("\"url\":\"", afIdx);
                        if (urlIdx != -1) {
                            int endIdx = json.indexOf("\"", urlIdx + 7);
                            String audioUrl = json.substring(urlIdx + 7, endIdx);
                            // Force proxy through Invidious if needed (local=true is usually for frontend but let's check if URL is direct)
                            System.out.println("Extracted URL: " + audioUrl.substring(0, 100) + "...");
                            
                            System.out.println("Verifying URL...");
                            HttpURLConnection audioConn = (HttpURLConnection) new URL(audioUrl).openConnection();
                            audioConn.setRequestMethod("GET");
                            audioConn.setRequestProperty("Range", "bytes=0-1024");
                            audioConn.setConnectTimeout(5000);
                            int code = audioConn.getResponseCode();
                            System.out.println("Response: " + code);
                            if (code == 200 || code == 206) {
                                System.out.println("✅ VERIFICATION SUCCESSFUL!");
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
        System.out.println("❌ No working audio stream found.");
    }
}
