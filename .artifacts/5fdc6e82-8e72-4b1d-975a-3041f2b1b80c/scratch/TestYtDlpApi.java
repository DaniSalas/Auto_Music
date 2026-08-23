import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TestYtDlpApi {
    public static void main(String[] args) {
        try {
            String q = URLEncoder.encode("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "UTF-8");
            String target = "https://co.wuk.sh/api/json";
            System.out.println("Testing Cobalt JSON API");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
