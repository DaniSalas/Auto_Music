import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindWorkingResult {
    public static void main(String[] args) {
        try {
            System.out.println("Searching for: U2 with or without you");
            URL url = new URL("https://www.youtube.com/youtubei/v1/search?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20240821.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"query\":\"U2 with or without you\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                int count = 0;
                while ((line = in.readLine()) != null && count < 5) {
                    if (line.contains("videoId\":\"")) {
                        int start = line.indexOf("videoId\":\"") + 10;
                        String vId = line.substring(start, line.indexOf("\"", start));
                        System.out.println("Found ID: " + vId);
                        check(vId);
                        count++;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void check(String vId) throws Exception {
        URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        String p = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\"}},\"videoId\":\"" + vId + "\"}";
        try (OutputStream os = conn.getOutputStream()) { os.write(p.getBytes("UTF-8")); }
        if (conn.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String res = in.readLine();
            if (res.contains("\"url\":\"https://")) {
                System.out.println("  ✅ WORKED!");
            } else {
                System.out.println("  ❌ Failed.");
            }
        }
    }
}
