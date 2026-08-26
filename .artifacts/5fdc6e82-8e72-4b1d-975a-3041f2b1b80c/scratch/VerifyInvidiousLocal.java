import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyInvidiousLocal {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M"; // U2 - With or Without You
        String instance = "https://inv.nadeko.net"; // Very stable Chilean instance
        
        try {
            System.out.println("Testing Invidious local proxy for: " + videoId);
            URL url = new URL(instance + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("\"url\":\"")) {
                         int start = line.indexOf("\"url\":\"") + 7;
                         int end = line.indexOf("\"", start);
                         String audioUrl = line.substring(start, end);
                         if (audioUrl.contains("googlevideo.com")) {
                             // Force local proxying by using the instance's own redirect
                             String localUrl = instance + "/latest_version?id=" + videoId + "&itag=140&local=true";
                             System.out.println("✅ PROPOSED LOCAL PROXY URL: " + localUrl);
                             
                             System.out.println("Verifying local proxy stream...");
                             HttpURLConnection proxyConn = (HttpURLConnection) new URL(localUrl).openConnection();
                             proxyConn.setRequestMethod("GET");
                             proxyConn.setRequestProperty("Range", "bytes=0-1024");
                             int code = proxyConn.getResponseCode();
                             System.out.println("Proxy Stream Response: " + code);
                             if (code == 200 || code == 206) {
                                 System.out.println("⭐⭐⭐ SUCCESS! INVIDIOUS LOCAL PROXY WORKS! ⭐⭐⭐");
                                 return;
                             }
                         }
                    }
                }
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        System.out.println("❌ Verification failed.");
    }
}
