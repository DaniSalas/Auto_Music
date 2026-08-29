import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifySimpleVideo {
    public static void main(String[] args) {
        String videoId = "jNQXAC9IVRw"; // Me at the zoo
        try {
            System.out.println("Testing Simple Video: " + videoId);
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String p = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\"}},\"videoId\":\"" + videoId + "\"}";
            conn.getOutputStream().write(p.getBytes("UTF-8"));
            
            if (conn.getResponseCode() == 200) {
                 BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                 String line;
                 while ((line = in.readLine()) != null) {
                     if (line.contains("\"url\":\"https://")) {
                         System.out.println("  ✅ SUCCESS! Simple video works.");
                         return;
                     }
                 }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("  ❌ FAILED.");
    }
}
