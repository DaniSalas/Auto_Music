import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyVrNew {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            System.out.println("Testing ANDROID_VR with full context...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\",\"osName\":\"Android\",\"osVersion\":\"12\",\"androidSdkVersion\":31,\"deviceMake\":\"Oculus\",\"deviceModel\":\"Quest 3\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ Status: OK");
                    if (json.contains("\"url\":\"https://")) {
                        System.out.println("⭐⭐⭐ SUCCESS! ⭐⭐⭐");
                        return;
                    }
                } else {
                    System.out.println("❌ Status: " + json.substring(json.indexOf("\"status\":\"") + 10, json.indexOf("\"", json.indexOf("\"status\":\"") + 10)));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
