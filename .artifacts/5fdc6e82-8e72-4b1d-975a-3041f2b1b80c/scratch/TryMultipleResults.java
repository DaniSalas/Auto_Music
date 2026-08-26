import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TryMultipleResults {
    public static void main(String[] args) {
        try {
            System.out.println("Searching for U2 - With or Without You...");
            URL url = new URL("https://music.youtube.com/youtubei/v1/search?key=AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_MUSIC\",\"clientVersion\":\"7.01.52\",\"hl\":\"en\",\"gl\":\"US\"}},\"query\":\"with or without you u2\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                String json = sb.toString();
                
                // Extract multiple videoIds
                int idx = json.indexOf("videoId\":\"");
                int count = 0;
                while (idx != -1 && count < 3) {
                    int end = json.indexOf("\"", idx + 10);
                    String vId = json.substring(idx + 10, end);
                    System.out.println("Result " + (count+1) + ": " + vId);
                    tryResolve(vId);
                    idx = json.indexOf("videoId\":\"", end);
                    count++;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void tryResolve(String vId) throws Exception {
        System.out.println("  Trying to resolve: " + vId);
        URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + vId + "\"}";
        try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
        
        if (conn.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = in.readLine();
            if (line.contains("\"url\":\"https://")) {
                System.out.println("  ✅ SUCCESS! Working link found for result " + vId);
            } else {
                System.out.println("  ❌ No direct link.");
            }
        }
    }
}
