import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyHlsNew {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Searching for HLS Manifest for: " + videoId);
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("hlsManifestUrl\":\"")) {
                        int start = line.indexOf("hlsManifestUrl\":\"") + 17;
                        int end = line.indexOf("\"", start);
                        String hlsUrl = line.substring(start, end).replace("\\u0026", "&");
                        System.out.println("✅ FOUND HLS URL: " + hlsUrl);
                        
                        HttpURLConnection hlsConn = (HttpURLConnection) new URL(hlsUrl).openConnection();
                        System.out.println("HLS Response: " + hlsConn.getResponseCode());
                        if (hlsConn.getResponseCode() == 200) {
                            System.out.println("⭐⭐⭐ SUCCESS! HLS WORKS! ⭐⭐⭐");
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
