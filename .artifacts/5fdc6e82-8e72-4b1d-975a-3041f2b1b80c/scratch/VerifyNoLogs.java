import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyNoLogs {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String inst = "https://invidious.no-logs.com";
        try {
            URL url = new URL(inst + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = reader.readLine();
                if (line.contains("adaptiveFormats")) {
                    System.out.println("✅ Found adaptiveFormats on no-logs.com");
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ FAILED");
    }
}
