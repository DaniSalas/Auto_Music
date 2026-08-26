import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DiagnosticScrape {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Starting Diagnostic for: " + videoId);
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int code = conn.getResponseCode();
            System.out.println("HTTP Response Code: " + code);

            if (code == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                boolean foundPlayerResponse = false;
                while ((line = in.readLine()) != null) {
                    if (line.contains("ytInitialPlayerResponse = ")) {
                        foundPlayerResponse = true;
                        int start = line.indexOf("ytInitialPlayerResponse = ");
                        int end = line.indexOf(";</script>", start);
                        if (end != -1) {
                            String json = line.substring(start + 26, end);
                            System.out.println("JSON Length: " + json.length());
                            
                            if (json.contains("streamingData")) {
                                System.out.println("✅ Found streamingData");
                                if (json.contains("signatureCipher")) {
                                    System.out.println("🔒 CONTAINS SIGNATURE CIPHER (Encrypted)");
                                } else if (json.contains("\"url\":\"https://")) {
                                    System.out.println("🔓 CONTAINS DIRECT URLS (Unencrypted)");
                                }
                            } else {
                                System.out.println("❌ No streamingData found in player response.");
                                if (json.contains("playabilityStatus")) {
                                    int sIdx = json.indexOf("status\":");
                                    System.out.println("Status Snippet: " + json.substring(sIdx, Math.min(json.length(), sIdx + 100)));
                                }
                            }
                        }
                        break;
                    }
                }
                if (!foundPlayerResponse) {
                    System.out.println("❌ Could not find ytInitialPlayerResponse in HTML.");
                }
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
