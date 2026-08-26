import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestKavin {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://pipedapi.kavin.rocks/api/v1/videos/dQw4w9WgXcQ");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            System.out.println("Code: " + conn.getResponseCode());
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                System.out.println("Success! Content length: " + in.readLine().length());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
