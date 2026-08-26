import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RealDownloadTest {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.privacyredirect.com",
            "https://inv.thepixora.com",
            "https://invidious.no-logs.com",
            "https://invidious.tiekoetter.com"
        };

        for (String inst : pool) {
            try {
                System.out.println("Attempting download from: " + inst);
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                
                int code = conn.getResponseCode();
                System.out.println("  Code: " + code);
                
                if (code == 200 || code == 206) {
                    InputStream in = conn.getInputStream();
                    byte[] buffer = new byte[1024];
                    int read = in.read(buffer);
                    
                    if (read > 0) {
                        String start = new String(buffer, 0, Math.min(read, 50));
                        if (start.startsWith("<!DOCTYPE") || start.contains("Invidious") || start.contains("Error")) {
                            System.out.println("  ❌ Fake data (HTML/Error)");
                            in.close();
                            continue;
                        }
                        
                        System.out.println("  ✅ Valid Binary Stream! Saving to temp/u2_test.m4a...");
                        FileOutputStream out = new FileOutputStream("temp/u2_test.m4a");
                        out.write(buffer, 0, read);
                        int total = read;
                        while ((read = in.read(buffer)) != -1 && total < 512 * 1024) { // Download 512KB
                            out.write(buffer, 0, read);
                            total += read;
                        }
                        out.close();
                        in.close();
                        System.out.println("  ✅ Download finished: " + total + " bytes.");
                        System.out.println("⭐⭐⭐ PROOF GENERATED SUCCESSFULLY ⭐⭐⭐");
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("  ❌ Failed: " + e.getMessage());
            }
        }
        System.out.println("❌ All pool instances failed.");
    }
}
