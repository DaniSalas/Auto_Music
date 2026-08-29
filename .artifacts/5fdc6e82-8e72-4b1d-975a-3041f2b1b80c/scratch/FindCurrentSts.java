import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FindCurrentSts {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/watch?v=uzF0M-9fO_M");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("signatureTimestamp")) {
                    int start = line.indexOf("signatureTimestamp") + 19;
                    System.out.println("DEBUG: " + line.substring(start, start + 20));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
