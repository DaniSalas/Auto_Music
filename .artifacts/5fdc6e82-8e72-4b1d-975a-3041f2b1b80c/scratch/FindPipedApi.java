import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindPipedApi {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://piped.video/assets/index-C8BB73ZQ.js");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                int idx = line.indexOf("api_url:\"");
                if (idx != -1) {
                    System.out.println("API URL: " + line.substring(idx, line.indexOf("\"", idx + 9 + 1)));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
