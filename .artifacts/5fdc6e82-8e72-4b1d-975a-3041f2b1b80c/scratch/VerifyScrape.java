import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyScrape {
    public static void main(String[] args) {
        String videoId = "dQw4w9WgXcQ";
        try {
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            String html = sb.toString();
            int idx = html.indexOf("signatureCipher\":\"");
            if (idx != -1) {
                int end = html.indexOf("\"", idx + 18);
                String cipher = html.substring(idx + 18, end);
                System.out.println("CIPHER: " + cipher);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
