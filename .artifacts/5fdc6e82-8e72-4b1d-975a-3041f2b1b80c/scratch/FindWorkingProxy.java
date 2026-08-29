import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindWorkingProxy {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {
            "https://invidious.jing.rocks",
            "https://iv.n8pjl.ca",
            "https://inv.tux.pizza",
            "https://invidious.io.lol",
            "https://invidious.no-logs.com",
            "https://invidious.privacydev.net",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de"
        };
        
        for (String inst : pool) {
            try {
                System.out.println("Testing: " + inst);
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("  Code: " + code);
                if (code == 200 || code == 206) {
                    InputStream in = conn.getInputStream();
                    byte[] buf = new byte[100];
                    int r = in.read(buf);
                    String head = new String(buf, 0, Math.max(0, r));
                    if (!head.contains("<html") && !head.contains("<!DOCTYPE")) {
                        System.out.println("  ✅ FOUND WORKING PROXY: " + inst);
                        return;
                    } else {
                        System.out.println("  ❌ HTML detected");
                    }
                }
            } catch (Exception e) { System.out.println("  ❌ Error: " + e.getMessage()); }
        }
    }
}
