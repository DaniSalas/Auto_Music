import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyNerd {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String inst = "https://invidious.nerdvpn.de";
        try {
            System.out.println("Querying API: " + inst);
            URL url = new URL(inst + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                System.out.println("JSON received. Length: " + json.length());
                
                // Look for HLS
                if (json.contains("hlsUrl")) {
                    int start = json.indexOf("\"hlsUrl\":\"") + 10;
                    int end = json.indexOf("\"", start);
                    String hls = json.substring(start, end);
                    System.out.println("✅ FOUND HLS: " + hls);
                    
                    HttpURLConnection hlsConn = (HttpURLConnection) new URL(hls).openConnection();
                    System.out.println("HLS response: " + hlsConn.getResponseCode());
                    if (hlsConn.getResponseCode() == 200) {
                        System.out.println("⭐⭐⭐ SUCCESS! HLS IS ACTIVE! ⭐⭐⭐");
                        return;
                    }
                }
                
                // Fallback to adaptiveFormats
                if (json.contains("adaptiveFormats")) {
                    int start = json.indexOf("\"url\":\"https://");
                    while (start != -1) {
                        int end = json.indexOf("\"", start + 7);
                        String aUrl = json.substring(start + 7, end);
                        if (aUrl.contains("googlevideo.com")) {
                            System.out.println("✅ Found adaptive format URL.");
                            HttpURLConnection test = (HttpURLConnection) new URL(aUrl).openConnection();
                            test.setRequestMethod("GET");
                            test.setRequestProperty("Range", "bytes=0-1024");
                            int code = test.getResponseCode();
                            System.out.println("Response: " + code);
                            if (code == 200 || code == 206) {
                                System.out.println("⭐⭐⭐ SUCCESS! AUDIO IS ACTIVE! ⭐⭐⭐");
                                return;
                            }
                        }
                        start = json.indexOf("\"url\":\"https://", end);
                    }
                }
            } else {
                System.out.println("API Down: " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
