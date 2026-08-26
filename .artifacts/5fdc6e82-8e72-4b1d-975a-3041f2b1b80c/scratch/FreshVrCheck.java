import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FreshVrCheck {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M"; // U2 - With or Without You
        try {
            System.out.println("Requesting VR identity for: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3)");
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
                
                int gvIdx = json.indexOf("googlevideo.com/videoplayback");
                if (gvIdx != -1) {
                    int urlStart = json.lastIndexOf("\"url\":\"", gvIdx) + 7;
                    int urlEnd = json.indexOf("\"", urlStart);
                    String streamUrl = json.substring(urlStart, urlEnd).replace("\\u0026", "&");
                    System.out.println("✅ EXTRACTED URL: " + streamUrl.substring(0, 100) + "...");
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                    audioConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    audioConn.setRequestMethod("GET");
                    audioConn.setRequestProperty("Range", "bytes=0-1024");
                    int code = audioConn.getResponseCode();
                    System.out.println("HTTP Code: " + code);
                    if (code == 200 || code == 206) {
                        System.out.println("⭐⭐⭐ VERIFICATION SUCCESSFUL! ⭐⭐⭐");
                        return;
                    }
                } else {
                    System.out.println("No googlevideo URL found in VR response.");
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED.");
    }
}
