import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifySavetube {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing Savetube API for: " + videoId);
            URL url = new URL("https://yt-api.savetube.me/video/info/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                String json = sb.toString();
                System.out.println("✅ Savetube Success!");
                
                if (json.contains("\"url\":\"https://")) {
                    int uStart = json.indexOf("\"url\":\"https://") + 7;
                    String streamUrl = json.substring(uStart, json.indexOf("\"", uStart)).replace("\\/", "/");
                    System.out.println("✅ Found Stream URL: " + streamUrl.substring(0, 50) + "...");
                    
                    downloadSample(streamUrl);
                    return;
                }
            } else {
                 System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void downloadSample(String u) throws Exception {
        System.out.println("Downloading bytes...");
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream("temp/u2_savetube.m4a");
        byte[] buf = new byte[4096];
        int r, total = 0;
        while ((r = in.read(buf)) != -1 && total < 102400) {
            out.write(buf, 0, r);
            total += r;
        }
        out.close();
        in.close();
        System.out.println("✅ DOWNLOADED " + total + " BYTES TO temp/u2_savetube.m4a");
    }
}
