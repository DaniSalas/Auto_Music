import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyRedirect {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        String[] targets = {
            "https://pipedapi.kavin.rocks/latest_version?id=" + videoId + "&itag=140",
            "https://piped.video/latest_version?id=" + videoId + "&itag=140"
        };
        for (String target : targets) {
            try {
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setInstanceFollowRedirects(false);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                String location = conn.getHeaderField("Location");
                System.out.println("Location: " + location);
                if (location != null && location.contains("googlevideo.com")) {
                    System.out.println("✅ FOUND REDIRECT TO STREAM!");
                    return;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
