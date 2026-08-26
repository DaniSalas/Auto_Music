import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyF5 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        // Attempt to access latest_version with local proxy
        String target = "https://invidious.f5.si/latest_version?id=" + videoId + "&itag=140&local=true";
        try {
            System.out.println("Testing F5 si: " + target);
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            conn.setConnectTimeout(6000);
            int code = conn.getResponseCode();
            System.out.println("HTTP Code: " + code);
            if (code == 200 || code == 206) {
                System.out.println("✅ SUCCESS! F5 si works!");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
