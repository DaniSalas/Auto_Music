import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyPipedStream {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            String target = "https://piped.video/latest_version?id=" + videoId + "&itag=140";
            System.out.println("Testing Piped direct stream: " + target);
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            conn.setConnectTimeout(5000);
            
            int code = conn.getResponseCode();
            System.out.println("Response Code: " + code);
            System.out.println("Content-Type: " + conn.getContentType());
            if (code == 200 || code == 206) {
                System.out.println("✅ SUCCESS: This URL is a direct stream!");
                return;
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
