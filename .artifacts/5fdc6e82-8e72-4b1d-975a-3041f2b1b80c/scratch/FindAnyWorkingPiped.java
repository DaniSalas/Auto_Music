import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindAnyWorkingPiped {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.kavin.rocks",
            "https://piped-api.lunar.icu",
            "https://pipedapi.mha.fi",
            "https://pipedapi.recloud.it",
            "https://pipedapi.pablo.casa",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.rivo.cc",
            "https://pipedapi.moe.xyz"
        };
        for (String i : instances) {
            try {
                System.out.println("Testing: " + i);
                URL url = new URL(i + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line = in.readLine();
                    if (line != null && line.contains("audioStreams")) {
                        System.out.println("✅ FOUND WORKING PIPED: " + i);
                        return;
                    }
                } else {
                     System.out.println("  Code: " + conn.getResponseCode());
                }
            } catch (Exception e) {}
        }
    }
}
