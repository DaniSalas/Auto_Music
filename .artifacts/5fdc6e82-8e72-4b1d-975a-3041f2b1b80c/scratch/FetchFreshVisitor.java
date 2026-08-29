import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchFreshVisitor {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/?theme=true");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("VISITOR_DATA\":\"")) {
                        int start = line.indexOf("VISITOR_DATA\":\"") + 15;
                        int end = line.indexOf("\"", start);
                        System.out.println("VISITOR: " + line.substring(start, end));
                        return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
