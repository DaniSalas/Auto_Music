import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchSts {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/watch?v=uzF0M-9fO_M");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("sts\":")) {
                    int start = line.indexOf("sts\":") + 5;
                    int end = line.indexOf(",", start);
                    if (end == -1) end = line.indexOf("}", start);
                    System.out.println("CURRENT STS: " + line.substring(start, end));
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
