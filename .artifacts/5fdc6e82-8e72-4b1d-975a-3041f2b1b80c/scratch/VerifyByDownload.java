import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyByDownload {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com",
            "https://invidious.f5.si",
            "https://invidious.no-logs.com",
            "https://iv.ggtyler.dev",
            "https://invidious.privacydev.net"
        };

        for (String inst : instances) {
            try {
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                System.out.println("Testing Instance: " + inst);
                
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                
                int code = conn.getResponseCode();
                System.out.println("  HTTP Code: " + code);
                
                if (code == 200 || code == 206) {
                    InputStream in = conn.getInputStream();
                    byte[] header = new byte[100];
                    int read = in.read(header);
                    String start = new String(header, 0, Math.max(0, read));
                    
                    if (start.contains("Invidious") || start.contains("shutdown") || start.contains("Error") || start.contains("<!DOCTYPE")) {
                        System.out.println("  ❌ Fake success (text response).");
                        in.close();
                        continue;
                    }

                    System.out.println("  ✅ Likely real binary data! Downloading...");
                    FileOutputStream out = new FileOutputStream("temp/u2_sample.m4a");
                    out.write(header, 0, read);
                    int totalDownloaded = read;
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1 && totalDownloaded < 102400) {
                        out.write(buffer, 0, bytesRead);
                        totalDownloaded += bytesRead;
                    }
                    in.close();
                    out.close();
                    System.out.println("  ✅ Downloaded " + totalDownloaded + " bytes.");
                    return;
                }
            } catch (Exception e) {
                System.out.println("  ❌ Error: " + e.getMessage());
            }
        }
        System.out.println("❌ All Invidious instances failed.");
        
        // Try Piped latest_version
        try {
            String piped = "https://piped.video/latest_version?id=" + videoId + "&itag=140";
            System.out.println("Testing Piped: " + piped);
            HttpURLConnection conn = (HttpURLConnection) new URL(piped).openConnection();
            if (conn.getResponseCode() == 200) {
                System.out.println("  ✅ Piped Success!");
                return;
            }
        } catch (Exception e) {}
    }
}
