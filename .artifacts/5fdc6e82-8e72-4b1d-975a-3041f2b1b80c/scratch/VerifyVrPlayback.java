import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyVrPlayback {
    private static String VR_UA = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)";

    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ"; 
        try {
            System.out.println("Getting URL from ANDROID_VR for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", VR_UA);
            conn.setDoOutput(true);
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                
                int urlIdx = json.indexOf("\"url\":\"https://");
                if (urlIdx != -1) {
                    int urlEnd = json.indexOf("\"", urlIdx + 7);
                    String streamUrl = json.substring(urlIdx + 7, urlEnd).replace("\\u0026", "&");
                    System.out.println("✅ Extracted URL.");
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                    audioConn.setRequestProperty("User-Agent", VR_UA);
                    audioConn.setRequestMethod("GET");
                    audioConn.setRequestProperty("Range", "bytes=0-1024");
                    int code = audioConn.getResponseCode();
                    System.out.println("Playback HTTP Code: " + code);
                    if (code == 200 || code == 206) {
                        System.out.println("⭐⭐⭐ SUCCESS! ⭐⭐⭐");
                        testU2();
                        return;
                    }
                } else {
                    System.out.println("No URL in response. Full response snippet: " + json.substring(0, Math.min(json.length(), 500)));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }

    private static void testU2() {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("\nTesting U2...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", VR_UA);
            conn.setDoOutput(true);
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                
                if (json.contains("signatureCipher")) {
                    System.out.println("🔒 U2 is CIPHERED.");
                } else if (json.contains("googlevideo.com")) {
                    System.out.println("✅ U2 is UNPROTECTED!");
                } else {
                    System.out.println("❌ No streams for U2.");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
