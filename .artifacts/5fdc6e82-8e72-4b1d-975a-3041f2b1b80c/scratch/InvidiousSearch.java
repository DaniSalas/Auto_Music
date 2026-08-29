import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class InvidiousSearch {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://invidious.jing.rocks",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com",
            "https://invidious.projectsegfau.lt"
        };
        for (String inst : instances) {
            try {
                System.out.println("Testing: " + inst);
                URL url = new URL(inst + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line = in.readLine();
                    if (line != null && line.contains("adaptiveFormats")) {
                        System.out.println("  ✅ Found adaptiveFormats!");
                    }
                } else {
                    System.out.println("  ❌ HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) { System.out.println("  ❌ " + e.getMessage()); }
        }
    }
}
