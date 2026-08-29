import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiagnosticPiped {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://piped-api.lunar.icu",
            "https://piped-api.mha.fi",
            "https://piped-api.garudalinux.org",
            "https://piped-api.arkenfox.eu",
            "https://piped-api.ucloud.casa"
        };
        for (String instance : instances) {
            try {
                System.out.println("Testing: " + instance);
                URL url = new URL(instance + "/streams/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line = in.readLine();
                    if (line != null && line.contains("audioStreams")) {
                        System.out.println("  ✅ SUCCESS!");
                        return;
                    }
                } else {
                    System.out.println("  ❌ HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) {
                System.out.println("  ❌ Error: " + e.getMessage());
            }
        }
    }
}
