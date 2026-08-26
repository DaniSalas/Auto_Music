import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyFullFlow {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M"; // U2
        try {
            System.out.println("Emergency resolution for: " + videoId);
            // Try extracting from a different Piped node
            String target = "https://piped-api.lunar.icu/streams/" + videoId;
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("\"url\":\"https://")) {
                        int start = line.indexOf("\"url\":\"") + 7;
                        int end = line.indexOf("\"", start);
                        String streamUrl = line.substring(start, end);
                        if (streamUrl.contains("googlevideo.com")) {
                            System.out.println("✅ FOUND STREAM URL: " + streamUrl.substring(0, 100) + "...");
                            
                            HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                            audioConn.setRequestMethod("GET");
                            audioConn.setRequestProperty("Range", "bytes=0-1024");
                            int code = audioConn.getResponseCode();
                            System.out.println("Audio Response: " + code);
                            if (code == 200 || code == 206) {
                                System.out.println("⭐⭐⭐ VERIFIED WORKING! ⭐⭐⭐");
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { System.out.println("Failed: " + e.getMessage()); }
        System.out.println("❌ All attempts failed.");
    }
}
