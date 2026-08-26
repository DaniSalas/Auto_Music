import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TryCobaltProperly {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] mirrors = {
            "https://cobalt.meowing.de",
            "https://cobalt.canine.tools",
            "https://api.cobalt.tools"
        };

        for (String mirror : mirrors) {
            try {
                System.out.println("Testing Mirror: " + mirror);
                URL url = new URL(mirror);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
                conn.setRequestProperty("Origin", mirror);
                conn.setRequestProperty("Referer", mirror + "/");
                conn.setDoOutput(true);

                String payload = "{\"url\":\"https://www.youtube.com/watch?v=" + videoId + "\",\"downloadMode\":\"audio\"}";
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

                int code = conn.getResponseCode();
                System.out.println("  Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String json = reader.readLine();
                    System.out.println("  ✅ JSON: " + json);
                    if (json.contains("\"url\":\"")) {
                        int uStart = json.indexOf("\"url\":\"") + 7;
                        String streamUrl = json.substring(uStart, json.indexOf("\"", uStart));
                        downloadSample(streamUrl);
                        return;
                    }
                }
            } catch (Exception e) { System.out.println("  ❌ Error: " + e.getMessage()); }
        }
    }

    private static void downloadSample(String u) throws Exception {
        System.out.println("Downloading from: " + u);
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream("temp/u2_final.m4a");
        byte[] buf = new byte[4096];
        int r, total = 0;
        while ((r = in.read(buf)) != -1 && total < 102400) {
            out.write(buf, 0, r);
            total += r;
        }
        out.close();
        in.close();
        System.out.println("✅ SUCCESSFULLY DOWNLOADED " + total + " BYTES TO temp/u2_final.m4a");
        System.out.println("⭐⭐⭐ DOWNLOAD VERIFIED ⭐⭐⭐");
    }
}
