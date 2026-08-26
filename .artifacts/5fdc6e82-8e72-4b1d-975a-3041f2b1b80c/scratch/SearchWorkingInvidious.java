import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SearchWorkingInvidious {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://invidious.flokinet.to",
            "https://invidious.tiekoetter.com",
            "https://inv.nadeko.net",
            "https://invidious.privacyredirect.com",
            "https://invidious.fdn.fr",
            "https://inv.pistasjis.net",
            "https://invidious.no-logs.com"
        };
        for (String inst : instances) {
            try {
                System.out.println("Testing: " + inst);
                URL url = new URL(inst + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                System.out.println("  Code: " + code);
                if (code == 200) {
                    System.out.println("  ✅ SUCCESS!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("  ❌ FAIL: " + e.getMessage());
            }
        }
    }
}
