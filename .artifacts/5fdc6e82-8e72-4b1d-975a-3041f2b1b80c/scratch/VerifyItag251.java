import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyItag251 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {"https://invidious.nerdvpn.de", "https://yewtu.be", "https://inv.nadeko.net"};
        for (String inst : pool) {
            try {
                System.out.println("Testing itag 251 on: " + inst);
                String target = inst + "/latest_version?id=" + videoId + "&itag=251&local=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                    System.out.println("  ✅ SUCCESS!");
                    return;
                }
            } catch (Exception e) {}
        }
        System.out.println("❌ FAILED.");
    }
}
