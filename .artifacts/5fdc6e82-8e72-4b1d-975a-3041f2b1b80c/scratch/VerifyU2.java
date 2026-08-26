import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyU2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] pool = {"https://pipedapi.mha.fi", "https://piped-api.lunar.icu", "https://pipedapi.leptons.xyz"};
        
        for (String inst : pool) {
            try {
                System.out.println("Testing " + inst + " for U2...");
                URL url = new URL(inst + "/api/v1/videos/" + videoId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    in.close();
                    
                    String json = sb.toString();
                    if (json.contains("audioStreams")) {
                        int urlIdx = json.indexOf("\"url\":\"https://");
                        int urlEnd = json.indexOf("\"", urlIdx + 7);
                        String streamUrl = json.substring(urlIdx + 7, urlEnd);
                        System.out.println("✅ Found Stream: " + streamUrl.substring(0, 100) + "...");
                        
                        HttpURLConnection test = (HttpURLConnection) new URL(streamUrl).openConnection();
                        test.setRequestMethod("GET");
                        test.setRequestProperty("Range", "bytes=0-1024");
                        int code = test.getResponseCode();
                        System.out.println("Code: " + code);
                        if (code == 200 || code == 206) {
                            System.out.println("⭐⭐⭐ SUCCESS! U2 IS PLAYABLE! ⭐⭐⭐");
                            return;
                        }
                    }
                }
            } catch (Exception e) { System.out.println("Failed: " + inst); }
        }
        System.out.println("❌ Verification failed.");
    }
}
