import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyJing {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String inst = "https://invidious.jing.rocks";
        try {
            System.out.println("Querying Jing: " + inst);
            URL url = new URL(inst + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String json = sb.toString();
                System.out.println("JSON length: " + json.length());
                if (json.contains("adaptiveFormats")) {
                    System.out.println("✅ Found formats.");
                    int urlIdx = json.indexOf("\"url\":\"https://");
                    if (urlIdx != -1) {
                        int urlEnd = json.indexOf("\"", urlIdx + 7);
                        String aUrl = json.substring(urlIdx + 7, urlEnd);
                        System.out.println("Testing stream: " + aUrl.substring(0, 100) + "...");
                        HttpURLConnection test = (HttpURLConnection) new URL(aUrl).openConnection();
                        test.setRequestMethod("GET");
                        test.setRequestProperty("Range", "bytes=0-1024");
                        int code = test.getResponseCode();
                        System.out.println("Response: " + code);
                        if (code == 200 || code == 206) {
                            System.out.println("⭐⭐⭐ SUCCESS! JING API WORKS! ⭐⭐⭐");
                            return;
                        }
                    }
                }
            } else {
                System.out.println("HTTP Code: " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
