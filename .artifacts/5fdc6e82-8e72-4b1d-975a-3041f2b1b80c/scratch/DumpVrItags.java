import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DumpVrItags {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String json = sb.toString();
            
            System.out.println("Itags found in VR response:");
            int idx = json.indexOf("\"itag\":");
            while (idx != -1) {
                int end = json.indexOf(",", idx);
                System.out.println("Found itag: " + json.substring(idx + 7, end).trim());
                idx = json.indexOf("\"itag\":", end);
            }
            
            if (json.contains("\"status\":\"OK\"")) {
                System.out.println("Status is OK!");
                if (json.contains("\"url\":")) {
                    System.out.println("URLs ARE PRESENT!");
                } else {
                    System.out.println("NO URLs FOUND (Likely ciphered or restricted)");
                }
            } else {
                System.out.println("Status is NOT OK.");
            }
            
        } catch (Exception e) { e.printStackTrace(); }
    }
}
