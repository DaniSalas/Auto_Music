import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestDrgnsAPI {
    public static void main(String[] args) {
        try {
            // drgns.space might redirect to the actual API node
            URL url = new URL("https://pipedapi.drgns.space/api/v1/videos/dQw4w9WgXcQ");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            System.out.println("Status: " + conn.getResponseCode());
            if (conn.getResponseCode() == 200) {
                 System.out.println("✅ Drgns is UP!");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
