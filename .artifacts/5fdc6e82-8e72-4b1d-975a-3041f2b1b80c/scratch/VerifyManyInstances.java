import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyManyInstances {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] invidious = {
            "https://invidious.f5.si",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com",
            "https://invidious.privacydev.net",
            "https://invidious.flokinet.to",
            "https://invidious.perennialte.ch",
            "https://inv.tux.pizza"
        };
        String[] piped = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.mha.fi",
            "https://piped-api.lunar.icu",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.recloud.it",
            "https://pipedapi.pablo.casa"
        };

        System.out.println("--- TESTING INVIDIOUS INSTANCES ---");
        for (String inst : invidious) {
            testInvidious(inst, videoId);
        }

        System.out.println("\n--- TESTING PIPED INSTANCES ---");
        for (String inst : piped) {
            testPiped(inst, videoId);
        }
    }

    private static void testInvidious(String instance, String videoId) {
        try {
            System.out.print("Testing " + instance + "... ");
            URL url = new URL(instance + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code == 200) {
                System.out.println("UP! Checking data...");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line != null && line.contains("adaptiveFormats")) {
                    System.out.println("  ✅ Found adaptiveFormats");
                    int urlIdx = line.indexOf("\"url\":\"");
                    if (urlIdx != -1) {
                        int endIdx = line.indexOf("\"", urlIdx + 7);
                        String audioUrl = line.substring(urlIdx + 7, endIdx);
                        verifyStream(audioUrl);
                    }
                } else {
                    System.out.println("  ❌ No adaptiveFormats found");
                }
                in.close();
            } else {
                System.out.println("DOWN (Code " + code + ")");
            }
        } catch (Exception e) { System.out.println("ERROR: " + e.getMessage()); }
    }

    private static void testPiped(String instance, String videoId) {
        try {
            System.out.print("Testing " + instance + "... ");
            URL url = new URL(instance + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code == 200) {
                System.out.println("UP! Checking data...");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                if (line != null && line.contains("audioStreams")) {
                    System.out.println("  ✅ Found audioStreams");
                    int urlIdx = line.indexOf("\"url\":\"");
                    if (urlIdx != -1) {
                        int endIdx = line.indexOf("\"", urlIdx + 7);
                        String audioUrl = line.substring(urlIdx + 7, endIdx);
                        verifyStream(audioUrl);
                    }
                } else {
                    System.out.println("  ❌ No audioStreams found");
                }
                in.close();
            } else {
                System.out.println("DOWN (Code " + code + ")");
            }
        } catch (Exception e) { System.out.println("ERROR: " + e.getMessage()); }
    }

    private static void verifyStream(String audioUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(audioUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            conn.setConnectTimeout(3000);
            int code = conn.getResponseCode();
            System.out.println("  Stream verification: " + code);
            if (code == 200 || code == 206) {
                System.out.println("  ⭐⭐⭐ SUCCESS! Working stream found! ⭐⭐⭐");
            }
        } catch (Exception e) { System.out.println("  Stream verification FAILED: " + e.getMessage()); }
    }
}
