import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyLatestVersion {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://invidious.projectsegfau.lt",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com",
            "https://invidious.f5.si",
            "https://invidious.perennialte.ch"
        };
        for (String inst : instances) {
            try {
                System.out.print("Testing " + inst + "... ");
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-1024");
                conn.setConnectTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200 || code == 206) {
                    System.out.println("  ✅ SUCCESS!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("  ❌ FAIL");
            }
        }
    }
}
