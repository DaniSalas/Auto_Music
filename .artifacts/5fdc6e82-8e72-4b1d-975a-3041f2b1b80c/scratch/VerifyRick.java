import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyRick {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String target = "https://invidious.projectsegfau.lt/latest_version?id=" + videoId + "&itag=140&local=true";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            System.out.println("Rick Response: " + conn.getResponseCode());
            if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                System.out.println("✅ RICK IS PLAYABLE!");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
