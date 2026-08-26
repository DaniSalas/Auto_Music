import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyNadeko {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String target = "https://inv.nadeko.net/latest_version?id=" + videoId + "&itag=140&local=true";
        try {
            System.out.println("Testing Nadeko Proxy: " + target);
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            conn.setConnectTimeout(6000);
            
            int code = conn.getResponseCode();
            System.out.println("HTTP Code: " + code);
            if (code == 200 || code == 206) {
                System.out.println("⭐⭐⭐ SUCCESS! U2 IS FULLY PLAYABLE VIA NADEKO PROXY! ⭐⭐⭐");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }
}
