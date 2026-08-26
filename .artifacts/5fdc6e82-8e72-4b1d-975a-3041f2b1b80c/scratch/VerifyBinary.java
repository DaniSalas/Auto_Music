import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyBinary {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {
            "https://invidious.nerdvpn.de",
            "https://yt.chocolatemoo53.com",
            "https://invidious.no-logs.com",
            "https://inv.nadeko.net"
        };
        for (String inst : pool) {
            try {
                System.out.println("Testing Binary Stream: " + inst);
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                
                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                    InputStream in = conn.getInputStream();
                    byte[] buf = new byte[8];
                    int r = in.read(buf);
                    if (r >= 4) {
                         String magic = new String(buf, 4, 4); // ftyp
                         if (buf[4] == 'f' && buf[5] == 't' && buf[6] == 'y' && buf[7] == 'p') {
                             System.out.println("  ✅ VALID MP4/M4A MAGIC BYTES FOUND!");
                             System.out.println("⭐⭐⭐ SUCCESS! USE THIS INSTANCE! ⭐⭐⭐");
                             return;
                         } else {
                             System.out.println("  ❌ Invalid magic bytes: " + new String(buf, 0, Math.min(r, 8)));
                         }
                    }
                } else {
                    System.out.println("  ❌ Code: " + conn.getResponseCode());
                }
            } catch (Exception e) { System.out.println("  ❌ Error: " + e.getMessage()); }
        }
    }
}
