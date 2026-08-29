import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindStsInJs {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/s/player/e937390a/player_ias.vflset/es_ES/base.js");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                int idx = line.indexOf("signatureTimestamp:");
                if (idx != -1) {
                    System.out.println("FOUND: " + line.substring(idx, Math.min(line.length(), idx + 30)));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
