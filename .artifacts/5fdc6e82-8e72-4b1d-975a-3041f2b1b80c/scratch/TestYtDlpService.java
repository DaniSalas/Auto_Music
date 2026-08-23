import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestYtDlpService {
    public static void main(String[] args) {
        String[] publicServices = {
            "https://ytdlp.tubearchivist.io",
            "https://p.s8.ro",
            "https://yt.artemislabs.eu"
        };
        for (String s : publicServices) {
            try {
                System.out.println("Testing: " + s);
                HttpURLConnection conn = (HttpURLConnection) new URL(s).openConnection();
                conn.setConnectTimeout(3000);
                System.out.println("Code: " + conn.getResponseCode());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
